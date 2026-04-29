# ragAdmin 查询改写架构设计

## 1. 目标

通过查询预处理和改写提升检索召回质量，同时保障用户输入的安全性和合规性：

- **查询预处理**：对用户原始 query 进行 PII 脱敏和内容过滤，防止敏感信息泄露和不文明内容进入后续管线
- **查询改写**：将原始查询从多角度重新表述或生成假设文档，以弥补用户查询与文档表达之间的语义鸿沟

## 2. 查询预处理

查询预处理在改写之前执行，是用户 query 进入检索管线的第一道关卡。

### 2.1 管线位置

```
用户原始 query
    │
    ▼
【Query 预处理：PII 脱敏 + 内容过滤】← 新增
    │
    ▼
QueryRewritingService.rewrite()
    │
    ▼
多路召回 → RRF 融合 → ...
```

预处理放在改写之前，确保脱敏后的干净 query 进入后续所有环节（改写、检索、日志、LLM 调用）。

### 2.2 PII 脱敏

通过正则匹配检测并替换个人身份信息（PII, Personally Identifiable Information）：

| 类型 | 模式 | 替换为 |
|------|------|--------|
| 身份证号 | `\d{17}[\dXx]` | `[身份证号]` |
| 手机号 | `1[3-9]\d{9}` | `[手机号]` |
| 邮箱 | `\S+@\S+\.\S+` | `[邮箱]` |
| 银行卡号 | `\d{16,19}` | `[银行卡号]` |
| IP 地址 | `\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}` | `[IP地址]` |

**设计要点**：
- 纯正则实现，不依赖外部模型，延迟为零
- 替换文本保留语义占位符，不影响后续检索和改写的语义理解（如"我的手机号 13800138000 还能收到通知吗" → "我的[手机号]还能收到通知吗"）
- 脱敏是透明的：日志中记录脱敏后的 query，不记录原始 PII

### 2.3 内容过滤

检测用户 query 中的不文明用语和违规内容：

| 层级 | 检测方式 | 处理策略 |
|------|----------|----------|
| 脏话/不文明用语 | 本地敏感词库匹配 | 替换为 `***` |
| 辱骂/攻击性内容 | 敏感词库 + 可选 LLM 辅助判断 | 拦截，返回提示 |
| 注入攻击（prompt injection） | 关键模式匹配 | 标记 + 日志告警 |

**敏感词库管理**：
- 默认内置基础词库（常见脏话、辱骂用语）
- 支持通过配置文件扩展：`rag.retrieval.query-preprocess.blocked-words`
- 支持通过配置文件添加自定义脱敏规则：`rag.retrieval.query-preprocess.custom-patterns`

### 2.4 容错设计

- 预处理失败时（如正则异常）：跳过预处理，使用原始 query，日志告警
- PII 脱敏和内容过滤均独立运行，一个失败不影响另一个
- 预处理结果不影响改写的回退逻辑：改写失败时回退到脱敏后的 query，而非原始 query

### 2.5 配置

```yaml
rag:
  retrieval:
    query-preprocess:
      enabled: true
      pii-mask:
        enabled: true
        mask-placeholder: "[已脱敏]"   # 自定义占位符，不设则按类型使用不同占位符
      content-filter:
        enabled: true
        block-enabled: false           # true 时直接拦截违规 query，false 时仅替换
        blocked-words:                 # 扩展敏感词列表
          - "脏话A"
          - "脏话B"
```

## 3. 改写策略

### 3.1 Multi-Query Decomposition

- 将用户的原始问题从不同角度重新表述，生成 N 个语义相同但表达不同的替代查询
- 每个替代查询独立执行检索，结果合并后去重（按 chunkId，保留最高分）
- 适用场景：用户查询表述模糊、用词不精确、需要多角度覆盖

### 3.2 HyDE（Hypothetical Document Embedding）

- 使用 LLM 生成一段假设性的回答文档，将此文档作为检索查询
- 假设文档的语义空间更接近真实文档，能提升检索的语义匹配质量
- 适用场景：用户查询过于简短、口语化，与文档正式表述差异大

### 3.3 组合模式

- `MULTI_QUERY_AND_HYDE`：同时启用两种策略，生成多查询 + 假设文档
- 所有查询结果统一合并去重

## 4. 配置

### 知识库级别

`kb_knowledge_base.retrieval_query_rewriting_mode` 字段控制每个知识库的改写模式：

| 模式 | 说明 |
|------|------|
| `NONE` | 不改写（默认） |
| `MULTI_QUERY` | 仅 Multi-Query 分解 |
| `HYDE` | 仅 HyDE 假设文档 |
| `MULTI_QUERY_AND_HYDE` | 两者都启用 |

### 全局配置

知识库级 `retrievalQueryRewritingMode` 是唯一控制点，不再使用全局开关。以下配置仅用于调参：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `rag.retrieval.query-rewriting.multi-query-count` | 3 | 生成的替代查询数量 |
| `rag.retrieval.query-rewriting.log-max-query-length` | 120 | 日志中查询文本截断长度 |

## 5. 处理流程

```
用户原始查询
    │
    ▼
QueryPreprocessService.preprocess(query)  ← PII 脱敏 + 内容过滤
    │
    ▼
QueryRewritingService.rewrite(query, mode)
    │
    ├── NONE → 返回原始查询
    │
    ├── MULTI_QUERY → LLM 生成 N 个替代查询
    │
    ├── HYDE → LLM 生成假设文档
    │
    └── MULTI_QUERY_AND_HYDE → 两者都执行
    │
    ▼
对每个查询独立执行 RetrievalService.retrieveSingleQuery()
    │
    ▼
合并所有结果：按 chunkId 去重，保留最高分
    │
    ▼
返回合并后的 RetrievalResult
```

## 6. 容错设计

- 无可用聊天模型时：跳过改写，使用原始查询
- LLM 调用失败时：捕获异常，回退到原始查询
- Multi-Query 解析失败时：使用已成功解析的查询（可能为空，退化为原始查询）
- HyDE 生成失败时：跳过假设文档查询

## 7. 核心组件

| 组件 | 职责 |
|------|------|
| `QueryPreprocessService` | 查询预处理入口，PII 脱敏 + 内容过滤（新增） |
| `QueryRewritingService` | 查询改写入口，调度 Multi-Query 和 HyDE |
| `QueryRewritingMode` | 改写模式枚举 |
| `QueryRewritingProperties` | 改写调参配置（multiQueryCount、logMaxQueryLength） |
| `QueryPreprocessProperties` | 预处理配置（PII 开关、内容过滤开关、敏感词列表）（新增） |
| `RetrievalService` | 在 retrieve() 中集成改写，多查询结果合并 |

## 8. Prompt 模板

| 模板 | 路径 | 用途 |
|------|------|------|
| Multi-Query | `prompts/ai/retrieval/multi-query-decomposition.st` | 指导 LLM 从不同角度重述查询 |
| HyDE | `prompts/ai/retrieval/hyde-generation.st` | 指导 LLM 生成假设性回答文档 |
