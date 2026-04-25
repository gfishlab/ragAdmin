# 纠正记录

## [C-001] 后台问答入口已下线

- 日期：2026-04-21
- 场景：前后台问答接口统一
- 错误表现：早期后台存在 `/api/admin/chat/**` 问答入口
- 根因：问答应统一归前台，后台只管知识库和文档运维
- 纠正动作：后台问答入口下线，所有问答走 `/api/app/chat/**`
- 后续约束：新增问答功能只走 `/api/app` 域
- 适用范围：前端路由、API 设计、验收文档

## [C-002] 分块策略不要硬编码在 DocumentParseProcessor 中

- 日期：2026-04-21
- 场景：分块逻辑重构为策略模式
- 错误表现：早期 splitText/overlapTail 直接写在 DocumentParseProcessor 中，所有文档类型走同一段逻辑
- 根因：缺少策略抽象，无法按文档类型区分分块行为
- 纠正动作：抽取为 `DocumentChunkStrategy` 接口 + 5 个策略实现，通过 `DocumentChunkStrategyResolver` 按优先级选择
- 后续约束：新增分块策略实现 `DocumentChunkStrategy` 接口，不修改 Processor
- 适用范围：文档分块相关改动

## [C-003] ChunkContext 需要携带 parseMode

- 日期：2026-04-21
- 场景：PDF 文档需要区分 OCR 和 TEXT 模式走不同分块策略
- 错误表现：`ChunkContext` 最初只有 document + signals + properties，无法在 `supports()` 中区分 parseMode
- 根因：parseMode 存在于清洗后 Document 的 metadata 中，未传递到 ChunkContext
- 纠正动作：ChunkContext 增加 `parseMode` 字段，由 DocumentParseProcessor 从文档 metadata 提取并传入
- 后续约束：路由所需的信息必须在 ChunkContext 中显式传递
- 适用范围：任何需要新路由字段的场景

## [C-004] ChatCompletionResult 使用 content 而非 text

- 日期：2026-04-22
- 场景：QueryRewritingService 调用 LLM 获取改写结果
- 错误表现：调用 `result.text()` 编译报错，record 字段名为 `content`
- 根因：未查阅 ChatCompletionResult record 定义，凭经验猜测字段名
- 纠正动作：改为 `result.content()`
- 后续约束：使用外部 record 类型前先查看其定义
- 适用范围：所有 LLM 调用结果的处理代码

## [C-005] RetrievalService 增量编辑导致文件损坏

- 日期：2026-04-22
- 场景：多次 Edit 工具修改 RetrievalService 的 retrieveSemantic 方法
- 错误表现：方法体被部分截断，返回语句缺失
- 根因：连续多次 old_string/new_string 替换在长方法中产生不可预期的边界重叠
- 纠正动作：使用 Write 工具完整重写文件
- 后续约束：对超过 50 行的方法做多次修改时，优先使用 Write 重写整个文件而非增量 Edit
- 适用范围：所有长文件的多轮编辑

## 反模式

### [A-001] 清洗层做分块的事，分块层做清洗的事

- 日期：2026-04-21
- 模式描述：在清洗阶段拆分文本，或在分块阶段修改文本内容
- 风险：职责混淆导致重复处理或遗漏，调试困难
- 更优做法：清洗只负责去噪和格式规范化，分块只负责切分和重叠，严格单向数据流
- 适用范围：新增 CleanerStep 或 ChunkStrategy 时

### [A-002] 全量聊天历史直接灌入模型

- 日期：2026-04-21
- 模式描述：将 `chat_message` 表的全部历史消息不加裁剪地发送给 LLM
- 风险：超出上下文窗口限制，成本失控，响应质量下降
- 更优做法：使用分层记忆（PostgreSQL 长期 + Redis 短期 + 摘要压缩），窗口内只保留近期消息
- 适用范围：聊天链路相关改动

### [A-003] conversationId 只包含 userId

- 日期：2026-04-21
- 模式描述：用纯 `userId` 作为 `conversationId`
- 风险：同一用户的所有会话串在一起，无法区分不同对话
- 更优做法：使用 `userId + sessionId` 组合编码，通过 `ConversationIdCodec` 管理
- 适用范围：会话管理相关改动

### [A-004] 在日志中输出敏感信息

- 日期：2026-04-21
- 模式描述：日志中出现密码、Token 原文、API Key、MinIO 密钥等敏感值
- 风险：泄露到日志文件或监控平台
- 更优做法：敏感字段使用脱敏处理或完全不记录
- 适用范围：所有日志输出

### [A-005] 为已完成的 plan 长期保留过期文档

- 日期：2026-04-21
- 模式描述：已完成的 plan 文档继续留在 `docs/plans` 中不清理
- 风险：堆积过期方案，与实际代码状态不一致，误导后续开发
- 更优做法：完成后及时删除，长期结论回写到 `docs/architectures`
- 适用范围：所有 plan 文档的生命周期管理

### [A-006] 删除文档时只清理 PG 不清理 Milvus 和 ES

- 日期：2026-04-22
- 模式描述：文档删除或知识库删除时，只删除 PG 中的 chunk 和 ref 行，不删除 Milvus 中的向量数据和 ES 中的索引文档
- 风险：Milvus 向量孤立（占用存储且无法被关联），ES 文档残留（全文检索命中已删除的内容）
- 更优做法：使用 ChunkVectorizationService.deleteRefsByChunkIds() 同时清理 Milvus 向量和 PG ref，使用 ChunkSearchSyncService 清理 ES
- 适用范围：任何涉及文档或知识库删除的代码路径

### [A-007] 知识库 Response record 新增字段后忘记同步测试构造器

- 日期：2026-04-22
- 模式描述：KnowledgeBaseResponse 是 record 类型，新增字段后测试代码中的构造器调用参数数量不匹配，编译通过但运行时抛 InstantiationException
- 风险：所有使用该 record 构造器的测试全部失败，阻塞 CI
- 更优做法：修改 record 后立即搜索所有构造器调用点（包括测试）并更新参数列表；或改用 builder 模式
- 适用范围：任何 record 类型的字段变更

### [A-008] RetrievalService 新增依赖后忘记注入测试 Mock

- 日期：2026-04-22
- 模式描述：RetrievalService 使用 @Autowired 字段注入，新增依赖后测试中缺少对应的 @Mock 和 ReflectionTestUtils.setField()，导致 NullPointerException
- 风险：测试 NPE 不指向真实缺失的 mock，排查需反复读堆栈
- 更优做法：新增 @Autowired 依赖时，同步在测试类添加 @Mock 字段和 setUp() 中的 setField()，并用 lenient() 设置 pass-through 默认行为
- 适用范围：RetrievalService 及类似使用字段注入 + 反射测试的服务类
