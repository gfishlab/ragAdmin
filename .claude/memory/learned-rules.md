# 已学规则

## [R-001] 工程笔记路径

- 日期：2026-04-20
- 规则：跨项目共享的工程笔记沉淀到 `/Users/gfish/codes/mds/raw/notes`，项目内部规则和纠偏留在 `.claude/memory`
- 来源：用户指定
- 适用范围：所有需要沉淀技术总结、选型论证、面试知识点的场景
- 备注：该路径已在 CLAUDE.md 中注册

## [R-002] 文档导入三件套职责边界

- 日期：2026-04-21
- 规则：文档导入链路严格分为 loading → cleaning → chunking 三阶段，清洗不拆文本，分块不改文本
- 来源：架构设计文档 `rag-admin-document-cleaning-architecture.md` 和 `rag-admin-document-chunking-architecture.md`
- 适用范围：任何涉及文档处理的新策略、新 CleanerStep 或新 ChunkStrategy

## [R-003] 分块策略不继承 Spring AI TextSplitter

- 日期：2026-04-21
- 规则：自定义 `DocumentChunkStrategy` 接口，不继承 Spring AI 的 `TextSplitter`。分块决策需要文档类型、解析模式、清洗信号等上下文，`TextSplitter` 的 `String → List<String>` 签名丢失全部决策信息
- 来源：架构设计文档 `rag-admin-document-chunking-architecture.md` 第 3 节
- 适用范围：任何新增分块策略

## [R-004] 知识库主链路仅支持同步文本 Embedding

- 日期：2026-04-21
- 规则：`EmbeddingExecutionMode.SYNC_TEXT` 是知识库绑定链路的唯一合法模式，异步批量模式允许登记但不可用于知识库
- 来源：`async-embedding-extension-plan.md` Task 2 实施结论
- 适用范围：知识库创建、模型绑定校验

## [R-005] 前后台统一用户源 + 登录入口隔离

- 日期：2026-04-21
- 规则：前后台共享 `sys_user` / `sys_role`，通过 sa-token 的 loginType 区分入口（`/api/admin/auth` vs `/api/app/auth`），后台接口基于权限码鉴权
- 来源：`access-boundary-plan.md` 实施结论
- 适用范围：任何新增 API 接口的认证授权设计

## [R-007] 任务执行自主判断并行与线性

- 日期：2026-04-21
- 规则：每次任务会话中，主 Agent 根据任务结构自主判断是否开启多 Agent 并行模式。任务有独立子模块、无写冲突时优先并行；有严格依赖链时走线性。不需要用户额外指示
- 来源：用户明确要求
- 适用范围：所有非简单任务的执行阶段
- 备注：详细规则写入 `.claude/rules/subagent-routing.md`

## [R-008] 三端数据同步采用同步写入模式

- 日期：2026-04-22
- 规则：PG/Milvus/ES 三端数据同步使用同步写入（虚拟线程），不使用 Outbox 或事件驱动。失败由任务重试机制兜底
- 来源：三端同步实施决策
- 适用范围：文档上传、重解析、文档删除、知识库删除涉及的多端同步操作

## [R-009] ES 操作受 enabled 开关条件化控制

- 日期：2026-04-22
- 规则：所有 ES 操作（索引管理、数据同步、删除、检索）通过 `rag.search-engine.elasticsearch.enabled` 开关控制，disabled 时所有方法为空操作
- 来源：ElasticsearchClient 和 ChunkSearchSyncService 设计
- 适用范围：任何新增 ES 集成点

## [R-006] 向量方案锁定 Milvus

- 日期：2026-04-21
- 规则：向量存储使用 Milvus 引用模型，PostgreSQL 不存向量本体。分块参数（maxChunkChars / overlapChars / minChunkChars）按 embedding provider 区分配置
- 来源：项目实施过程
- 适用范围：向量存储、检索、分块配置相关改动
