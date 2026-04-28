# 检索冲突检测实施计划

## 目标

基于 `docs/architectures/rag-admin-retrieval-conflict-resolution-architecture.md`，实现 Phase 1（Prompt 级矛盾感知）和 Phase 2（LLM NLI 步骤）。

## 实施步骤

### Step 1：配置层

**新建** `ConflictDetectionProperties.java`（`retrieval/config/`）

```yaml
rag:
  retrieval:
    conflict-detection:
      enabled: false
      method: PROMPT          # PROMPT | LLM_NLI
      llm-nli:
        model-provider: ${rag.chat.default-provider}
        temperature: 0.0
        top-p: 0.1
        max-pairs: 20
        confidence-threshold: 0.7
      resolution:
        enabled: true
```

字段：
- `enabled`：总开关
- `method`：PROMPT（Phase 1）或 LLM_NLI（Phase 2）
- `llm-nli.model-provider`：复用已有 Provider（当前即 Ollama Qwen3-Reranker-0.6B）
- `llm-nli.temperature`：0.0
- `llm-nli.top-p`：0.1
- `llm-nli.max-pairs`：最多检测的 chunk 对数
- `llm-nli.confidence-threshold`：低于此阈值的矛盾判断忽略
- `resolution.enabled`：是否启用基于元数据的自动消解

### Step 2：数据结构

**新建** `ConflictGroup.java`（`retrieval/model/`）

```java
public class ConflictGroup {
    private String groupId;
    private ConflictType conflictType;   // FACT, VERSION, SCOPE, AUTHORITY
    private double confidence;
    private String preferredChunkId;     // 自动消解后优先的 chunk（可为空）
    private String reason;               // 消解依据
    private List<Long> chunkIds;         // 冲突组内所有 chunk ID
}
```

**新建** `ConflictDetectionResult.java`（`retrieval/model/`）

```java
public class ConflictDetectionResult {
    private List<ConflictGroup> conflicts;
    private List<RetrievedChunk> resolvedChunks;  // 消解后的 chunk 列表
}
```

**扩展** `RetrievedChunk` record（RetrievalService.java line 278）

当前：`record RetrievedChunk(ChunkEntity chunk, double score)`

扩展为包含冲突标记：

```java
record RetrievedChunk(ChunkEntity chunk, double score, List<String> conflictGroupIds)
```

提供向后兼容的构造函数（无冲突标记时默认空列表）。

### Step 3：Prompt 模板

**新建** `resources/prompts/ai/retrieval/conflict-detection-system.st`

```
你是一个专业的文本矛盾检测专家。你需要判断提供的文本对是否存在逻辑矛盾。

分析步骤：
1. 判断两段文本的主体是否相同
2. 判断讨论的属性/话题是否相同
3. 判断在相同条件下结论是否相反

矛盾判断规则：
- 只有主体相同、属性相同、条件相同、结论相反时，才判定为 contradiction
- 条件不同（时间、版本、适用范围、角色）的不算矛盾，标记为 neutral
- 无法判断的标记为 neutral
```

**新建** `resources/prompts/ai/retrieval/conflict-detection-user.st`

```
请判断以下文本对是否存在逻辑矛盾。

文本A：{chunk_a}
文本B：{chunk_b}

请严格按照以下 JSON 格式输出，不要输出任何其他内容：
{{"subject_match": true/false, "attribute_match": true/false, "condition_match": true/false, "relation": "entailment/contradiction/neutral", "confidence": 0.0-1.0, "reason": "判断依据"}}
```

**新建** `resources/prompts/ai/retrieval/conflict-ie-system.st`（信息抽取）

```
你是一个专业的文本信息提取专家。从文本中提取结构化的主体、属性、结论信息。
```

**新建** `resources/prompts/ai/retrieval/conflict-ie-user.st`

```
从以下文本中提取结构化信息。

文本：{chunk_text}

请严格按照以下 JSON 格式输出，不要输出任何其他内容：
{{"subject": "主体", "attribute": "属性", "conclusion": "结论", "conditions": {{"key": "value"}}}}
```

### Step 4：核心服务

**新建** `ConflictDetectionService.java`（`retrieval/service/`）

主要方法：

```java
public ConflictDetectionResult detect(String query, List<RetrievedChunk> chunks)
```

内部流程：

1. **元数据预筛选**（不调模型，零成本）
   - 按 `documentId` 分组，检测同文档多版本：`documentVersionId` 不同即为候选冲突对
   - 按 `kbId` 分组，检测跨知识库的同文档冲突
   - 这一步直接利用 `ChunkEntity` 已有字段，不需要额外信息

2. **信息抽取**（调模型，只对元数据无法判断的 chunk）
   - 对每个 chunk 调用信息抽取 prompt，提取 `{subject, attribute, conclusion, conditions}`
   - 缓存结果，避免重复提取

3. **主体分组 + 两两 NLI 判断**（调模型）
   - 按 subject + attribute 分组
   - 只在同组内做两两 NLI 判断（减少调用量）
   - 输出 contradiction 的标记为冲突

4. **冲突消解**
   - 优先按元数据消解：版本号高的 > 版本号低的，createdAt 新的 > 旧的
   - 无法消解的保留冲突标记

**注入位置**：`RetrievalService.java`

当前流程（`retrieveSingleQuery`，line 92-120）：
```
多路召回 → Reranking → ParentChunkExpansion → buildContext
```

改为：
```
多路召回 → 【冲突检测】→ Reranking（可感知冲突标记）→ ParentChunkExpansion → buildContext
```

注意：架构文档确认冲突检测放在 reranking **之前**。

### Step 5：Phase 1 — Reranking Prompt 增强

**修改** `reranking-system.st`

在现有评分指令后追加矛盾感知：

```
额外要求：
- 如果发现片段间存在逻辑矛盾，请结合文档版本、来源权威性判断哪个更可信
- 在矛盾片段中，对更可信的给更高分
```

**修改** `app-knowledge-system.st`（Chat system prompt）

追加冲突处理规则：

```
冲突处理规则：
- 如果提供的知识片段中存在相互矛盾的结论，不要强行融合为一个答案
- 明确指出矛盾点，分别给出每条结论的来源和适用条件
- 如果能根据来源权威性判断，优先引用更权威的来源
- 如果无法判断，提示用户补充条件或转人工确认
```

Phase 1 完成后，即使 `conflict-detection.enabled=false`，prompt 级矛盾感知也生效。

### Step 6：单元测试

**新建** `ConflictDetectionServiceTest.java`

测试用例：

1. **元数据预筛选 — 同文档多版本冲突**
   - 输入：同 documentId、不同 documentVersionId 的两个 chunk
   - 预期：自动识别为 VERSION 冲突，选择版本号高的

2. **元数据预筛选 — 无冲突**
   - 输入：不同 documentId 的 chunk
   - 预期：不生成冲突组

3. **NLI 矛盾检测 — 事实矛盾**
   - 输入："小明喜欢吃苹果" vs "小明不喜欢吃苹果"
   - 预期：relation=contradiction

4. **NLI 矛盾检测 — 条件不同不算矛盾**
   - 输入："2023 版不支持" vs "2024 版支持"
   - 预期：relation=neutral

5. **冲突消解 — 版本优先**
   - 冲突组中一个来自 v2、一个来自 v1
   - 预期：preferredChunkId 指向 v2 的 chunk

6. **冲突消解 — 时间优先**
   - 冲突组中无法判断版本，但 createdAt 不同
   - 预期：选择更新的

### Step 7：集成验证

1. 启动应用，通过 API 上传两个版本的同名文档（内容有矛盾）
2. 查询触发混合检索
3. 验证冲突检测结果：日志输出、上下文中的冲突标记
4. 验证 Chat 答案是否正确处理了矛盾（显式说明冲突，而非强行融合）

## 文件改动清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `retrieval/config/ConflictDetectionProperties.java` | 冲突检测配置 |
| 新建 | `retrieval/model/ConflictType.java` | 冲突类型枚举 |
| 新建 | `retrieval/model/ConflictGroup.java` | 冲突组数据结构 |
| 新建 | `retrieval/model/ConflictDetectionResult.java` | 检测结果 |
| 新建 | `retrieval/service/ConflictDetectionService.java` | 核心服务 |
| 新建 | `resources/prompts/ai/retrieval/conflict-detection-system.st` | NLI system prompt |
| 新建 | `resources/prompts/ai/retrieval/conflict-detection-user.st` | NLI user prompt |
| 新建 | `resources/prompts/ai/retrieval/conflict-ie-system.st` | 信息抽取 system prompt |
| 新建 | `resources/prompts/ai/retrieval/conflict-ie-user.st` | 信息抽取 user prompt |
| 新建 | `retrieval/service/ConflictDetectionServiceTest.java` | 单元测试 |
| 修改 | `retrieval/service/RetrievalService.java` | 注入冲突检测 + 扩展 RetrievedChunk |
| 修改 | `resources/prompts/ai/retrieval/reranking-system.st` | Phase 1 矛盾感知 |
| 修改 | `resources/prompts/ai/chat/app-knowledge-system.st` | Phase 1 冲突处理规则 |
| 修改 | `resources/application.yml` | 新增 conflict-detection 配置段 |

## 依赖关系

```
Step 1 (配置) ─→ Step 2 (数据结构) ─→ Step 3 (Prompt) ─→ Step 4 (服务) ─→ Step 5 (Phase 1 prompt 增强)
                                                              │
                                                              └→ Step 6 (测试) ─→ Step 7 (集成验证)
```

Step 5（Phase 1 prompt 增强）独立于 Step 4，可以并行。

## 与架构文档的对应关系

| 架构文档阶段 | 本计划对应 |
|-------------|-----------|
| Phase 1：Prompt 级矛盾感知 | Step 5 |
| Phase 2：轻量 NLI 步骤 | Step 1-4, 6-7 |
| Phase 3：结构化元数据预提取 | 不在本计划范围内 |
