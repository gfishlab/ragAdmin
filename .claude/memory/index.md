# 工程记忆索引

## 文件说明

- [已学规则](learned-rules.md) — 9 条稳定规则（R-001~R-009）
- [纠正记录](corrections.md) — 3 条纠正（后台问答下线、分块策略重构、ChunkContext parseMode）
- [阶段观察](observations.md) — 5 条观察（TextSplitter局限、中文句子检测、分块参数对齐、父子分块可行性、ES IK分词器）
- [反模式清单](anti-patterns.md) — 6 条反模式（A-001~A-006）
- [演化日志](evolution-log.md) — 9 条演化记录（2026-04-14 至 2026-04-22）

## 当前高频主题

- 文档导入三件套（loading → cleaning → chunking）已全部完成
- 三端数据同步（PG + Milvus + ES）已实现：上传同步、删除同步、知识库级联删除
- 混合检索基础设施就绪：Milvus 语义检索已运行，ES 全文索引已集成，RRF 融合待实施
- 分块策略自定义 DocumentChunkStrategy，不自建在框架接口上
- 同步写入模式（虚拟线程），不使用 Outbox 或事件驱动
