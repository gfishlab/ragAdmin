# ragAdmin 检索架构设计

## 1. 目标

从单一语义检索升级为多路召回 + 融合排序的混合检索架构，兼顾语义理解能力和精确匹配能力。

## 2. 检索路径

### 2.1 语义检索（Milvus）

- 算法：向量相似度 COSINE
- 适用场景：语义匹配、跨语言检索、同义词理解
- 实现方式：Embedding 模型将查询向量化，在 Milvus 中执行相似度搜索

### 2.2 全文检索（ES）

- 算法：BM25 关键词匹配
- 适用场景：精确术语匹配、编号查询、专有名词检索
- 实现方式：`chunk_text` 字段使用 IK 分词器（`ik_max_word` 索引 / `ik_smart` 搜索），ES 执行全文搜索
- 组件：`KeywordRetrievalStrategy` 调用 `ElasticsearchClient.searchWithScores()` 获取带分数的结果

### 2.3 混合检索（Milvus + ES）

- 算法：多路召回 + RRF（Reciprocal Rank Fusion）融合排序
- 适用场景：综合语义理解和精确匹配，获得最优召回质量
- 实现方式：分别从 Milvus 和 ES 独立召回候选集，通过 RRF 公式融合为统一排序结果
- 组件：`RrfFusionService` 负责融合，`RetrievalService` 按 KB 配置的模式分发

## 3. 当前状态

| 模块 | 状态 | 说明 |
|------|------|------|
| Milvus 语义检索 | 已实现 | `RetrievalService.retrieveSemantic()` |
| ES 全文索引 | 已实现 | `ElasticsearchClient`、`ChunkSearchSyncService` 数据同步 |
| ES 全文检索 | 已实现 | `ElasticsearchClient.searchWithScores()` + `KeywordRetrievalStrategy` |
| 混合检索融合 | 已实现 | `RrfFusionService` + `RetrievalService` 模式分发 |
| 检索模式配置 | 已实现 | `kb_knowledge_base.retrieval_mode` 字段，支持 SEMANTIC_ONLY / KEYWORD_ONLY / HYBRID |

## 4. 检索模式

系统支持三种检索模式，通过知识库的 `retrieval_mode` 字段配置：

| 模式 | 标识 | 召回源 | 适用场景 |
|------|------|--------|----------|
| 语义检索 | `SEMANTIC_ONLY` | Milvus | 语义理解类查询（默认） |
| 全文检索 | `KEYWORD_ONLY` | ES | 精确匹配类查询 |
| 混合检索 | `HYBRID` | Milvus + ES | 通用场景，综合最优 |

分发逻辑位于 `RetrievalService.retrieve()`，根据 `RetrievalMode.resolve(kb.getRetrievalMode())` 决定路径。

### 关键配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `rag.retrieval.rrf-k` | 60 | RRF 平滑参数 |
| `rag.retrieval.hybrid-top-k-multiplier` | 2 | HYBRID 模式下每路召回倍数（topK × multiplier） |

## 5. RRF 融合公式

混合检索使用 Reciprocal Rank Fusion 进行结果融合：

```
score = Σ 1 / (k + rank_i)
```

- `k = 60`：标准平滑参数，降低高排名结果的过度影响
- `rank_i`：每路召回中该文档的排名位置（从 1 开始）
- 对每路召回结果独立排名后，按 RRF 公式累加得分
- 最终按融合得分降序排列

### 融合流程

1. Milvus 召回 top-K × multiplier 语义候选集
2. ES 召回 top-K × multiplier 关键词候选集
3. 按 chunk_id 合并两路结果
4. 分别计算每路排名，套用 RRF 公式得出融合得分
5. 按融合得分降序排列，截取 top-K 返回最终结果

## 6. 未来扩展

### 6.1 Reranker 重排序

- 在 RRF 融合后引入 Reranker 模型
- 对候选集进行精排，提升检索精度
- 可选集成 Cross-Encoder 或 LLM-based Reranker
- `kb_knowledge_base.rerank_enabled` 字段已预留

### 6.2 查询改写增强

- Multi-Query 分解：将复杂查询拆分为多个子查询并行检索
- HyDE：生成假设文档用于检索，提升语义匹配质量

## 7. 检索架构依赖图

```
DocumentParseProcessor
  ├── persistChunks(PG)          ── PG 写入 chunk 数据
  ├── vectorize(Milvus)          ── Milvus 写入向量数据
  └── syncChunks(ES)             ── ES 写入全文索引

RetrievalService.retrieve()
  ├── retrieveSemantic()   ────── SEMANTIC_ONLY（默认）
  ├── KeywordRetrievalStrategy   KEYWORD_ONLY
  └── HYBRID 分发
        ├── retrieveSemantic()
        ├── KeywordRetrievalStrategy.retrieve()
        └── RrfFusionService.fuse()
```

数据流向：文档写入时三端同步（PG + Milvus + ES），检索时按知识库配置的模式从对应存储召回并融合。

## 8. 核心组件

| 组件 | 职责 |
|------|------|
| `RetrievalService` | 检索入口，按模式分发，构建上下文 |
| `KeywordRetrievalStrategy` | ES BM25 关键词检索，返回带分数的 chunk 列表 |
| `RrfFusionService` | RRF 融合算法，合并多路召回结果 |
| `ElasticsearchClient` | ES REST 客户端，`searchWithScores()` 返回带分数的文档 |
| `RetrievalMode` | 检索模式枚举（SEMANTIC_ONLY / KEYWORD_ONLY / HYBRID） |
| `RetrievalProperties` | 检索配置（topK、rrfK、hybridTopKMultiplier 等） |
