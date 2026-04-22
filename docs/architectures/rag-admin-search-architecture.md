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
- 实现方式：`chunk_text` 字段使用 standard analyzer，ES 执行全文搜索

### 2.3 混合检索（Milvus + ES）

- 算法：多路召回 + RRF（Reciprocal Rank Fusion）融合排序
- 适用场景：综合语义理解和精确匹配，获得最优召回质量
- 实现方式：分别从 Milvus 和 ES 独立召回候选集，通过 RRF 公式融合为统一排序结果

## 3. 当前状态

| 模块 | 状态 | 说明 |
|------|------|------|
| Milvus 语义检索 | 已实现 | `RetrievalService` 中完成向量检索逻辑 |
| ES 全文索引 | 基础设施已就绪 | `ElasticsearchClient`、`ChunkSearchSyncService` 已实现数据同步 |
| ES 全文检索 API | 已预留 | `ElasticsearchClient.search()` 接口已可用 |
| 混合检索融合 | 待实施 | 需要 RRF 融合层和统一排序逻辑 |

## 4. 预留的检索模式

系统预留三种检索模式，通过检索参数控制：

| 模式 | 标识 | 召回源 | 适用场景 |
|------|------|--------|----------|
| 语义检索 | `SEMANTIC_ONLY` | Milvus | 语义理解类查询 |
| 全文检索 | `KEYWORD_ONLY` | ES | 精确匹配类查询 |
| 混合检索 | `HYBRID` | Milvus + ES | 通用场景，综合最优 |

## 5. RRF 融合公式

混合检索使用 Reciprocal Rank Fusion 进行结果融合：

```
score = Σ 1 / (k + rank_i)
```

- `k = 60`：标准平滑参数，降低高排名结果的过度影响
- `rank_i`：每路召回中该文档的排名位置
- 对每路召回结果独立排名后，按 RRF 公式累加得分
- 最终按融合得分降序排列

### 融合流程

1. Milvus 召回 top-K 语义候选集
2. ES 召回 top-K 关键词候选集
3. 按 chunk_id 合并两路结果
4. 分别计算每路排名，套用 RRF 公式得出融合得分
5. 按融合得分降序返回最终结果

## 6. 未来扩展

### 6.1 Reranker 重排序

- 在 RRF 融合后引入 Reranker 模型
- 对候选集进行精排，提升检索精度
- 可选集成 Cross-Encoder 或 LLM-based Reranker

### 6.2 知识库级别检索模式配置

- 支持每个知识库独立配置默认检索模式
- 不同业务场景可选择最适合的检索策略
- 配置存储在 `kb` 表的检索策略字段中

## 7. 检索架构依赖图

```
DocumentParseProcessor
  ├── persistChunks(PG)          ── PG 写入 chunk 数据
  ├── vectorize(Milvus)          ── Milvus 写入向量数据
  └── syncChunks(ES)             ── ES 写入全文索引

RetrievalService
  ├── Milvus 语义检索 ────────── SEMANTIC_ONLY
  ├── ES 全文检索     ────────── KEYWORD_ONLY
  └── 混合检索
        ├── Milvus 召回
        ├── ES 召回
        └── RRF 融合排序 ─────── HYBRID
```

数据流向：文档写入时三端同步，检索时按模式从对应存储召回并融合。
