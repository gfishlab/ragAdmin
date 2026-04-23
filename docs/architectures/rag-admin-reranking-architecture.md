# ragAdmin Reranking 架构设计

## 1. 目标

在检索结果返回前，对候选文档片段进行精排，提升最终返回结果的相关性质量。

## 2. 两种 Reranking 方案对比

### 2.1 LLM-based Reranking（当前实现）

通用大语言模型通过 prompt 对候选段落打分排序。

**工作原理**：构建包含编号段落的 prompt → LLM 输出 JSON 评分 → 解析排序

**优点**：
- 无需部署额外专用模型，复用现有 LLM 基础设施
- 评分标准通过 prompt 灵活调整（如"对技术文档给更高分"）
- 对复杂语义理解能力强（能理解隐含关联、推理关系）
- 初始成本低，不需要训练或下载专用模型

**缺点**：
- **延迟高**：1-3 秒/次，是检索链路最大延迟来源
- **成本高**：每次评分消耗大量 token（prompt + 全部段落）
- **输出不稳定**：LLM 可能输出非标准 JSON，需要容错解析
- **不可复现**：相同输入可能得到不同分数
- **评分尺度不一致**：不同 prompt 版本、不同模型产生的分数范围不同

### 2.2 Cross-Encoder Reranking（规划中）

专门训练用于相关性打分的编码器模型（如 Qwen3-Reranker-0.6B、BGE-reranker-v2-m3）。

**工作原理**：将 (query, passage) 对输入模型 → 模型输出一个相关性分数 → 按分数排序

**优点**：
- **延迟低**：10-100 毫秒/对，比 LLM 快 10-100 倍
- **成本低**：无 token 消耗，仅模型推理计算
- **输出稳定**：相同输入始终产生相同分数
- **准确度高**：专门在相关性判断任务上训练过，通常优于通用 LLM
- **评分尺度一致**：同一模型产出的分数可直接比较

**缺点**：
- 需要部署额外的专用模型（GPU 显存或额外服务）
- 评分标准固定，不能通过 prompt 灵活调整
- 模型选择和参数调优需要额外工作
- 对极复杂的语义推理可能不如大参数 LLM

### 2.3 对比总结

| 维度 | LLM-based | Cross-Encoder |
|------|-----------|---------------|
| 延迟 | 1-3 秒 | 10-100 毫秒 |
| 成本 | 高（token 计费） | 低（仅推理计算） |
| 准确度 | 高（依赖模型能力） | 高（专门训练） |
| 灵活性 | 极高（prompt 可调） | 低（固定行为） |
| 稳定性 | 输出有随机性 | 确定性输出 |
| 部署要求 | 无额外要求 | 需要专用模型服务 |
| 适用场景 | 开发测试、低频高价值查询 | 生产环境、高频实时查询 |

### 2.4 为什么实际业务需要 Reranking

检索阶段（向量相似度 / BM25）只能衡量"表面匹配度"，无法判断"真实相关性"：

1. **语义漂移**：向量相似度高但语义不相关的段落（如讨论"苹果公司"vs"苹果水果"）
2. **关键词误导**：BM25 匹配了关键词但上下文不相关
3. **排序偏差**：向量检索和 BM25 各自的排序都可能把次优结果排到前面
4. **上下文窗口有限**：送入 LLM 生成答案的 context 有长度限制，必须精选最相关段落

Reranking 作为"质检环节"，从候选集里挑出真正最有用的内容，直接提升最终回答质量。

## 3. 双模式架构设计

### 3.1 目标架构

```
RetrievalService
  → 检索结果 (候选段落)
  → RerankingService
      ├─ mode=LLM → LLMRerankingStrategy（当前实现）
      └─ mode=CROSS_ENCODER → CrossEncoderRerankingStrategy（规划中）
  → 精排后的结果
```

### 3.2 策略接口

```java
public interface RerankingStrategy {
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates);
}
```

### 3.3 模式选择

- 通过 `rag.retrieval.reranking.method` 配置：`LLM` | `CROSS_ENCODER`
- 默认 `LLM`（向后兼容）
- 知识库级别可覆盖全局配置

### 3.4 Cross-Encoder 集成方案

**模型部署**：通过 Ollama 本地部署（已就绪）

| 模型 | 参数量 | 大小 | 特点 |
|------|--------|------|------|
| `B-A-M-N/qwen3-reranker-0.6b-fp16` | 0.6B | 1.2 GB | 轻量，适合开发测试 |
| `dengcao/bge-reranker-v2-m3` | 568M | 1.2 GB | 多语言支持，生产可用 |

**调用方式**：通过 Ollama REST API，输入 (query, passage) 对，输出相关性分数。

**批量优化**：对 N 个候选段落并发调用，或使用 Ollama 批量推理。

## 4. 当前实现（LLM-based）

### 4.1 触发条件

同时满足以下条件时触发 reranking：

1. `kb_knowledge_base.rerank_enabled = true`（知识库级别开关）
2. `rag.retrieval.reranking.enabled = true`（全局开关）
3. 候选结果数 > 1

### 4.2 处理流程

```
检索结果 (RetrievedChunk 列表)
    │
    ▼
检查触发条件 (rerankEnabled + 全局enabled + size > 1)
    │
    ▼ 不满足 → 返回原始结果
    │
    ▼ 满足
截取 maxCandidates 个候选
    │
    ▼
构建 Reranking Prompt (编号 passages)
    │
    ▼
LLM 返回 JSON 评分 [{"index": 1, "score": 9.5}, ...]
    │
    ▼
解析评分，按分数排序取 topN
    │
    ▼
返回精排后的 RetrievedChunk 列表
```

### 4.3 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `rag.retrieval.reranking.enabled` | false | 全局开关 |
| `rag.retrieval.reranking.method` | LLM | 精排方法：LLM / CROSS_ENCODER |
| `rag.retrieval.reranking.top-n` | 3 | 精排后保留的结果数量 |
| `rag.retrieval.reranking.max-candidates` | 20 | 参与精排的最大候选数 |

### 4.4 容错设计

- 无可用聊天模型：跳过 reranking，返回原始排序
- LLM 调用失败：捕获异常，返回原始排序
- JSON 解析失败：跳过 reranking，返回原始排序
- 未被 LLM 评分的候选：追加到结果末尾

## 5. 核心组件

| 组件 | 职责 |
|------|------|
| `RerankingService` | Reranking 入口，调度评分策略和结果排序 |
| `RerankingProperties` | 全局配置（开关、方法、topN、maxCandidates） |
| `RerankingStrategy` | 评分策略接口（LLM / Cross-Encoder） |
| `RetrievalService` | 在 retrieveSingleQuery 中集成 reranking |

## 6. Prompt 模板

| 模板 | 路径 | 用途 |
|------|------|------|
| System | `prompts/ai/retrieval/reranking-system.st` | 评分标准和输出格式 |
| User | `prompts/ai/retrieval/reranking-user.st` | 查询 + 编号 passages |

## 7. 实施路线

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段一 | LLM-based Reranking 实现 | 已完成 |
| 阶段二 | 提取策略接口，RerankingService 改为策略分发 | 规划中 |
| 阶段三 | Cross-Encoder 策略实现（Ollama 集成） | 规划中 |
| 阶段四 | 混合 Reranking：Cross-Encoder 粗排 + LLM 精排 | 远期 |

## 8. 缓存机制（远期）

相同查询 + 相似候选集时复用评分结果，减少重复调用。
