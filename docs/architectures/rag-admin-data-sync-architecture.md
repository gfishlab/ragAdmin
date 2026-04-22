# ragAdmin 数据同步架构设计

## 1. 目标

实现 PG（关系存储）、Milvus（向量存储）、ES（全文索引）三端数据一致性，确保知识库中每个 chunk 在三端的状态保持同步。

## 2. 三端存储职责分工

### 2.1 PG `kb_chunk` 表

- 存储 chunk 原始文本内容
- 存储 chunk 元数据（JSONB 格式）
- 存储统计信息（token 数、字符数等）
- 作为 chunk 数据的唯一权威数据源

### 2.2 Milvus `kb_{kbId}_emb_{modelId}_d_{dim}` 集合

- 存储向量数据（embedding）
- 关联字段：`chunk_id`、`document_id`、`kb_id`
- 按知识库 + 嵌入模型 + 维度自动隔离为独立集合

### 2.3 ES `kb_{kbId}_chunks` 索引

- 全文检索字段：`chunk_text`（使用 standard analyzer）
- 关联字段：`chunk_id`、`kb_id`、`document_id`、`document_version_id`（long 类型）
- 控制字段：`enabled`（boolean）
- 元数据字段：`metadata_json`（keyword 类型）
- 按知识库自动隔离为独立索引

### 2.4 PG `kb_chunk_vector_ref` 表

- 跟踪 chunk → Milvus collection + vectorId 的映射关系
- 用于定位某个 chunk 在 Milvus 中的具体位置
- 支持向量数据的精确删除和更新

## 3. 同步触发点

### 3.1 上传 / 重解析

触发位置：`DocumentParseProcessor`

流程：

1. 文档解析完成后执行 `GENERATE_EMBEDDING` 步骤（已有）
2. 之后新增 `SYNC_SEARCH_ENGINE` 步骤
3. `SYNC_SEARCH_ENGINE` 调用 `ChunkSearchSyncService.syncChunks()` 将 chunk 数据同步写入 ES

### 3.2 文档删除

触发位置：`DocumentService.delete()`

流程：

1. 调用 `chunkVectorizationService.deleteRefsByChunkIds()` 同时删除 Milvus 向量数据和 PG ref 记录
2. 调用 `chunkSearchSyncService.deleteByDocument()` 删除该文档在 ES 中的所有 chunk

### 3.3 知识库删除

触发位置：`KnowledgeBaseService.delete()`

流程：

1. 执行文档删除的全部操作
2. 调用 `chunkSearchSyncService.deleteByKnowledgeBase()` 删除整个 ES 索引

## 4. 同步模型

采用**同步写入**方式，基于虚拟线程实现，不使用事件驱动或 Outbox 模式。

### 4.1 选型原因

- ragAdmin 定位为内部企业 RAG 系统
- 文档操作频率较低
- 同步写入 + 重试机制足够满足可靠性需求
- 避免引入消息队列等额外基础设施的运维复杂度

### 4.2 失败策略

- 任何一端写入失败，直接抛出异常
- 任务框架（parse task）自动重试整个解析任务
- 重试时 PG 事务回滚保证关系数据一致，Milvus 和 ES 按幂等方式处理

### 4.3 ES 写入与 PG 事务的关系

- ES 写入独立于 PG 事务（ES 本身无事务意识）
- PG 事务提交成功后触发 ES 写入
- ES 写入失败通过任务重试补偿

## 5. 一致性语义

采用**最终一致性**，通过任务重试机制保证。

- 正常路径：三端同步写入成功，数据即时一致
- 异常路径：任务重试确保最终三端数据收敛一致
- 不引入分布式事务或两阶段提交

## 6. ES 索引 Mapping

```
{
  "mappings": {
    "properties": {
      "chunk_text":            { "type": "text", "analyzer": "standard" },
      "chunk_id":              { "type": "long" },
      "kb_id":                 { "type": "long" },
      "document_id":           { "type": "long" },
      "document_version_id":   { "type": "long" },
      "enabled":               { "type": "boolean" },
      "metadata_json":         { "type": "keyword" }
    }
  }
}
```

## 7. 条件化开关

配置项：`rag.search-engine.elasticsearch.enabled`

- `true`：所有 ES 操作正常执行（索引创建、数据同步、删除）
- `false`：所有 ES 操作变为空操作（no-op），系统仅使用 PG + Milvus 双端运行

该开关确保 ES 基础设施不可用或未部署时，系统核心功能不受影响。
