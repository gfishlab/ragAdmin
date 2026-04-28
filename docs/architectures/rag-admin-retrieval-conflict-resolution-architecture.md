# ragAdmin 检索冲突解决架构设计

## 1. 问题定义

多路召回（向量 + 关键词 + 查询改写）会产生一批候选 chunk，这些 chunk 之间可能存在事实冲突。向量相似度只能衡量"主题相近"，不能衡量"逻辑一致"。

典型冲突场景：

| 类型 | 示例 | 特征 |
|------|------|------|
| 事实矛盾 | "小明喜欢吃苹果" vs "小明不喜欢吃苹果" | 同一主体、同一属性、相反结论 |
| 版本冲突 | "2023 版不支持该功能" vs "2024 版支持该功能" | 时间/版本条件不同 |
| 适用范围冲突 | "普通员工不能报销头等舱" vs "高管可以报销头等舱" | 主体范围不同 |
| 来源权威性冲突 | 正式制度文档 vs 个人培训笔记 | 来源可信度不同 |

## 2. 核心概念

### 2.1 NLI（Natural Language Inference，自然语言推理）

NLI 是 NLP（自然语言处理）的一个子任务，专门判断两段文本的逻辑关系：

- **entailment（蕴含）**：A 能推出 B
- **contradiction（矛盾）**：A 与 B 逻辑上互相排斥
- **neutral（中立）**：A 与 B 无直接逻辑关系

在检索冲突场景中，NLI 用于判断两个候选 chunk 是否属于 contradiction 关系。

### 2.2 信息抽取（Information Extraction, IE）

IE 是 NLP 的另一个子任务，用于从非结构化文本中提取结构化信息：

- **NER（命名实体识别）**：提取人名、组织、产品等实体
- **关系抽取**：提取实体间的关系
- **属性提取**：提取主体的属性值（如"主体=小明，属性=苹果偏好，结论=喜欢"）

在冲突检测中，IE 用于将 chunk 内容结构化，便于后续按主体、属性、条件分组和比较。

### 2.3 NLI 与 NLP 的关系

```
NLP（自然语言处理，整个领域）
  ├── 分词 / 词性标注
  ├── NER（命名实体识别）
  ├── 信息抽取（IE）
  ├── 文本分类
  ├── NLI（自然语言推理）← 检测 chunk 间矛盾的核心技术
  ├── 机器翻译
  └── ...
```

## 3. 冲突检测在检索管线中的位置

```
Query
  → QueryRewriting（Multi-Query / HyDE）
  → 多路召回（Vector + Keyword）
  → RRF 融合 + ID 去重
  → 【冲突检测】← 新增阶段
  → Reranking（LLM / Cross-Encoder，可感知冲突标记）
  → 上下文组装
  → LLM 生成答案
```

冲突检测放在 Reranking **之前**。原因：

1. **冲突信息可以传给 Reranking**：重排时知道哪些 chunk 是矛盾的，能把矛盾组中更可信的排前面，而不是无差别排序
2. **候选集大小可控**：RRF 融合后约 topK × hybridTopKMultiplier（默认 10 条），先按主体分组、只在同组内做 NLI 比较，实际检测 5~10 对，调用量不大
3. **企业级场景重质量轻吞吐**：内部知识库并发有限，答案准确性（尤其是合规、财务、政策类问题）比极限吞吐量更重要
4. **业务量大时可降级**：把 NLI 模型从 Qwen3-Reranker-0.6B 换成专用小模型（Erlangshen-330M，<10ms），或关闭 NLI 只保留 prompt 级矛盾感知

### 3.1 为什么不放在 Reranking 之后

放在 Reranking 之后的唯一优势是候选集更小（topK ≈ 5 条），但：

- Reranking 已经做完了，无法利用冲突信息调整排序，冲突检测只能打标
- 候选集从 10 条缩到 5 条，NLI 调用次数差距不大（同组内 5~10 对 vs 2~5 对）
- 企业内部场景的瓶颈不在 NLI 这一步，而在 embedding 检索和 Reranking

### 3.2 Temperature 与 Top_p 参数说明

NLI 判断和信息抽取需要确定性输出，参数设置如下：

| 任务 | temperature | top_p | 原因 |
|------|-------------|-------|------|
| NLI 矛盾判断 | 0.0 | 0.1 | 分类任务，需要确定性 |
| 信息抽取 | 0.0 | 0.1 | 结构化提取，需要稳定输出 |
| RAG 答案生成 | 0.3 ~ 0.7 | 0.9 | 需要自然流畅 |

**Temperature** 控制概率分布的尖锐程度：
- `temperature=0`：永远选概率最高的词，完全确定性
- `temperature=1`：保持原始概率分布
- `temperature=2`：概率分布被压平，低概率词获得更多机会

**Top_p（核采样）** 控制候选词池的大小，按累积概率从高到低截断：
- `top_p=0.9`：保留累积概率前 90% 的词（约 50-100 个候选）
- `top_p=0.1`：只保留累积概率前 10% 的词（约 3-5 个候选）
- `top_p=1.0`：不限制

**为什么 top_p 不能设为 0**：`top_p=0` 意思是"从累积概率 0% 的词中选"，即空集。不同 API 实现处理方式不同，可能报错、静默回退到 1.0、或行为未定义。`top_p=0.1` 是安全下限，配合 `temperature=0` 效果等同确定性输出。

## 4. 实施方案

### 4.1 短期方案：Prompt 级矛盾感知（成本最低）

在 Reranking prompt 和 Chat prompt 中加入矛盾感知指令，不引入新的模型调用。

#### 4.1.1 Reranking Prompt 增强

在现有 `reranking-system.st` 的评分指令中增加：

```
额外要求：
- 如果某两个片段在相同主体、相同属性上给出了相反结论，请在评分后额外标注
- 输出格式变更为：
[{"index": 1, "score": 9.5, "conflicts": [2]}, {"index": 2, "score": 7.0, "conflicts": [1]}]
- conflicts 数组填写与之矛盾的片段 index，无冲突则留空数组
```

#### 4.1.2 Chat Prompt 增强

在知识库问答的 system prompt 中加入冲突处理指令：

```
冲突处理规则：
- 如果提供的知识片段中存在相互矛盾的结论，不要强行融合为一个答案
- 明确指出矛盾点，分别给出每条结论的来源和适用条件
- 如果能根据来源权威性判断（如文档版本、发布时间），优先引用更权威的来源
- 如果无法判断，提示用户补充条件或转人工确认
```

#### 4.1.2 适用场景

- 快速验证冲突检测的价值
- 不增加额外模型调用，零额外成本和延迟
- 依赖 LLM 自身的矛盾识别能力，覆盖面有限

### 4.2 中期方案：轻量 NLI 步骤（推荐）

在 Reranking 之后、上下文组装之前，插入独立的矛盾检测步骤。

#### 4.2.1 架构

```
Reranking 结果（top-K）
  → ConflictDetectionService
      ├─ 主体/属性结构化提取（IE，通过大模型 few-shot prompt）
      ├─ 按主体+属性分组
      ├─ 组内两两 NLI 判断（contradiction check）
      └─ 冲突消解（元数据规则优先，无法消解则打标保留）
  → 上下文组装（带冲突标记）
```

#### 4.2.2 模型选择

通过项目已有的 Provider 体系调用大模型，无需额外部署专用模型：

| 步骤 | 实现方式 | 说明 |
|------|----------|------|
| 主体/属性提取 | 大模型 few-shot prompt | 输出结构化 JSON：`{subject, attribute, conclusion, conditions}` |
| NLI 矛盾判断 | 大模型 prompt | 输出三分类：`entailment / contradiction / neutral` |
| 冲突消解 | 规则引擎 | 基于元数据（版本、时间、来源类型）自动消解 |

**为什么现有对话模型就够用？**

NLI 本质上是一个三分类任务（entailment / contradiction / neutral），不一定要用专用 NLI 模型。项目已部署的 Ollama 对话模型（如 Qwen）通过 few-shot prompt 完全可以做矛盾判断，只需把 temperature 设成 0，它就是一个分类器。

选择现有对话模型而非专用小模型的原因：

- 项目已有 Provider 管理架构和 OpenAI 兼容协议调用链路，零额外部署
- 不需要维护新的模型服务（专用编码器模型无法通过 Ollama 提供 API）
- 大模型的中文 NLI 能力已经足够处理这类判断
- 专用 NLI 模型（如 DeBERTa-MNLI）主要是英文，中文场景需要额外适配
- 检索候选集规模小（通常 5~10 条），两两比较的调用量可控

**什么时候需要考虑专用模型？** 当检索量上来，发现每次调对话模型做矛盾判断的延迟或成本不可接受时，再部署轻量专用 NLI 模型替代。

#### 4.2.2.1 专用 NLI 模型选型（未来扩容参考）

如果后续需要更高性能或更低成本，以下是本地部署的专用 NLI / Reranker 模型选型。

##### 中文场景

| 模型 | 参数量 | 架构 | 最低显存 | 推理速度 | 部署方式 | 说明 |
|------|--------|------|----------|----------|----------|------|
| IDEA-CCNL/Erlangshen-Roberta-110M-NLI | 110M | Encoder | ~0.5 GB | <10ms | HuggingFace Transformers | 中文 NLI 专项，CMNLI 基准优秀 |
| IDEA-CCNL/Erlangshen-Roberta-330M-NLI | 330M | Encoder | ~1.5 GB | ~10ms | HuggingFace Transformers | 更高精度，CPU 可用 |
| Qwen/Qwen3-Reranker-0.6B | 0.6B | Decoder | ~1.5 GB (FP16) / ~1 GB (Q4) | ~50ms | Ollama (GGUF) / vLLM | 中文原生，Reranking + NLI 双能力 |
| Qwen/Qwen3-Reranker-4B | 4B | Decoder | ~8 GB (FP16) / ~4 GB (Q4) | ~100ms | Ollama (GGUF) / vLLM | 高精度，消费级 GPU |
| BAAI/bge-reranker-v2-m3 | 568M | Encoder (XLM-R) | ~1.5 GB | ~20ms | HuggingFace / 社区 Ollama | 中英双语，最成熟稳定的轻量方案 |
| BAAI/bge-reranker-v2-gemma | 2.51B | Decoder (Gemma) | ~8-12 GB | ~80ms | HuggingFace / vLLM | 高精度 Reranker |

##### 英文场景

| 模型 | 参数量 | 架构 | 最低显存 | 推理速度 | 部署方式 | 说明 |
|------|--------|------|----------|----------|----------|------|
| cross-encoder/nli-MiniLM2-L6-H768 | 22.7M | Encoder | ~0.1 GB | <5ms | HuggingFace Transformers | 极快，英文 NLI 轻量首选 |
| cross-encoder/nli-deberta-v3-base | 184M | Encoder | ~0.5 GB | ~10ms | HuggingFace Transformers | 英文 NLI 精度高 |
| dleemiller/FineCat-NLI-l | 350M | Encoder | ~1 GB | ~15ms | HuggingFace Transformers | 2025 MNLI SOTA |

##### 多语言（中英兼顾）

| 模型 | 参数量 | 架构 | 最低显存 | 推理速度 | 部署方式 | 说明 |
|------|--------|------|----------|----------|----------|------|
| MoritzLaurer/mDeBERTa-v3-base-xnli-multilingual-nli-2mil7 | 86M | Encoder | ~0.5 GB | ~10ms | HuggingFace Transformers | 100 种语言，XNLI 中文子集表现良好 |
| Qwen/Qwen3-Reranker-0.6B | 0.6B | Decoder | ~1 GB (Q4) | ~50ms | Ollama / vLLM | 中英双语原生支持，Reranking + NLI |
| BAAI/bge-reranker-v2-m3 | 568M | Encoder (XLM-R) | ~1.5 GB | ~20ms | HuggingFace | 中英双语，8K 上下文 |
| jinaai/jina-reranker-v2-base-multilingual | 278M | Encoder | ~1 GB | ~15ms | HuggingFace / TEI | 100+ 语言，超轻量 |

##### 综合推荐

| 场景 | 首选 | 备选 |
|------|------|------|
| **当前阶段（用现有 Ollama 对话模型）** | 已部署的 Qwen / 其他对话模型 | — |
| **中文纯 NLI 分类** | Erlangshen-330M-NLI | Erlangshen-110M-NLI |
| **英文纯 NLI 分类** | cross-encoder/nli-deberta-v3 | FineCat-NLI-l |
| **中英双语 NLI + Reranking** | Qwen3-Reranker-0.6B (Ollama) | bge-reranker-v2-m3 |
| **资源受限（CPU / 低显存）** | bge-reranker-v2-m3 | Erlangshen-110M-NLI |
| **最高精度** | Qwen3-Reranker-8B (FP16) | bge-reranker-v2-gemma |

##### 部署注意事项

- **编码器类模型**（Erlangshen、DeBERTa、mDeBERTa、BGE-m3）：不通过 Ollama 部署，需用 HuggingFace Transformers 或 TEI（Text Embeddings Inference）包装成 HTTP 服务
- **解码器类模型**（Qwen3-Reranker、BGE-gemma）：支持 Ollama GGUF 量化部署，也支持 vLLM 高性能推理
- **Ollama 对 Reranker 模型的支持**：非原生完整支持，Qwen3-Reranker 通过社区 GGUF 可用，BGE 系列通过社区 `qllama` 命名空间提供
- **专用 NLI 模型无法复用 OpenAI 兼容协议**：需自建 HTTP 接口或通过 TEI 提供 API，与项目现有 Provider 体系不完全兼容
- **因此当前阶段推荐用现有对话模型**，后续如需专用模型，优先选 Qwen3-Reranker（Ollama 部署、与现有架构兼容性最好）

#### 4.2.3 NLI 判断 Prompt 设计

```
你是一个专业的文本矛盾检测专家。判断以下两段文本是否存在逻辑矛盾。

文本A：{chunk_a}
文本B：{chunk_b}

请分析：
1. 两段文本的主体是否相同
2. 讨论的属性/话题是否相同
3. 在相同条件（时间、版本、适用范围）下，结论是否相反

输出 JSON：
{
  "subject_match": true/false,
  "attribute_match": true/false,
  "condition_match": true/false,
  "relation": "entailment/contradiction/neutral",
  "confidence": 0.0-1.0,
  "reason": "判断依据"
}
```

调用参数：`temperature=0.0`，`top_p=0.1`，确保输出确定性。

#### 4.2.4 信息抽取 Prompt 设计

```
从以下文本中提取结构化信息。

文本：{chunk_text}

提取要求：
- subject：讨论的主体（人、物、组织、概念）
- attribute：讨论的属性或话题
- conclusion：对该属性的结论或判断
- conditions：适用条件（时间、版本、范围、角色等）

输出 JSON：
{
  "subject": "小明",
  "attribute": "苹果偏好",
  "conclusion": "喜欢",
  "conditions": {"时间": "无特定时间限定"}
}
```

调用参数：`temperature=0.0`，`top_p=0.1`。

#### 4.2.5 冲突消解规则

按优先级自动消解：

| 规则 | 优先级 | 示例 |
|------|--------|------|
| 文档版本 | 最高 | 2024 版覆盖 2023 版 |
| 发布时间 | 高 | 最新发布的优先 |
| 来源类型 | 高 | 正式制度 > 培训材料 > 个人笔记 |
| 部门权威性 | 中 | 总部 > 分部 |
| 适用范围 | 中 | 全局 > 特定业务线 |

无法自动消解时，打上冲突标记，在生成阶段显式提示用户。

#### 4.2.6 冲突标记数据结构

```java
public class ConflictGroup {
    private String groupId;           // 冲突组 ID
    private String conflictType;      // FACT / VERSION | SCOPE | AUTHORITY
    private double confidence;        // 冲突判断置信度
    private String preferredChunkId;  // 自动消解后优先的 chunk（可为空）
    private String reason;            // 消解依据（可为空）
    private List<String> chunkIds;    // 冲突组内所有 chunk ID
}
```

### 4.3 长期方案：结构化元数据预提取

在分块阶段预提取主体/属性/结论的结构化信息，作为 chunk 元数据持久化。检索后直接利用元数据做分组和冲突判断，避免每次检索时重复提取。

这需要改动分块管线和 chunk 元数据 Schema，属于架构级变更。

## 5. 实施优先级

| 阶段 | 方案 | 改动范围 | 预期收益 | 风险 |
|------|------|----------|----------|------|
| Phase 1 | Prompt 级矛盾感知 | 仅改 prompt 模板 | 低成本验证价值 | 依赖 LLM 自身能力 |
| Phase 2 | 轻量 NLI 步骤 | 新增 ConflictDetectionService + 配置 | 系统级冲突检测 | 增加延迟和模型调用 |
| Phase 3 | 结构化元数据预提取 | 分块管线 + Schema 变更 | 检索时零额外开销 | 架构改动大 |

## 6. 配置设计

```yaml
rag:
  retrieval:
    conflict-detection:
      enabled: false                    # 总开关，Phase 1 可不依赖此开关
      method: PROMPT                    # PROMPT | LLM_NLI | DEDICATED_NLI | STRUCTURED
      # PROMPT: 仅 prompt 级矛盾感知（Phase 1）
      # LLM_NLI: 通过现有对话模型做 NLI 判断（Phase 2，推荐）
      # DEDICATED_NLI: 通过专用 NLI 模型做判断（未来扩容）
      # STRUCTURED: 基于预提取的结构化元数据（Phase 3）

      # LLM_NLI 模式配置（复用已有 Provider）
      llm-nli:
        model-provider: ${rag.chat.default-provider}  # 复用已有 provider（如 Ollama Qwen）
        temperature: 0.0                # NLI 分类任务，需要确定性输出
        top-p: 0.1
        max-pairs: 20                   # 最多检测的 chunk 对数
        confidence-threshold: 0.7       # 低于此阈值的矛盾判断忽略

      # DEDICATED_NLI 模式配置（未来扩容）
      dedicated-nli:
        endpoint: http://localhost:8090/nli  # 专用 NLI 服务地址
        model: qwen3-reranker-0.6b           # 模型标识
        language: auto                       # auto | zh | en，auto 时按知识库语言配置自动选择
        confidence-threshold: 0.7
        # 中文推荐：qwen3-reranker-0.6b / erlangshen-330m
        # 英文推荐：nli-deberta-v3 / finecat-nli
        # 多语言推荐：qwen3-reranker-0.6b / bge-reranker-v2-m3

      # 冲突消解规则
      resolution:
        enabled: true                   # 是否启用自动消解
        rules:
          - type: VERSION               # 按文档版本消解
            priority: 100
          - type: PUBLISH_TIME          # 按发布时间消解
            priority: 90
          - type: SOURCE_TYPE           # 按来源类型消解
            priority: 80
```

## 7. 与现有架构的关系

本设计是检索管线的扩展，与以下现有模块协同：

- **搜索架构**（`rag-admin-search-architecture.md`）：冲突检测在 RRF 融合之后
- **Reranking 架构**（`rag-admin-reranking-architecture.md`）：冲突检测在 Reranking 之后
- **查询改写架构**（`rag-admin-query-rewriting-architecture.md`）：查询改写增大候选集，冲突检测从中筛选
- **分块架构**（`rag-admin-document-chunking-architecture.md`）：长期方案需要分块管线配合
