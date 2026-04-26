# 阶段观察

## [O-001] Spring AI TextSplitter 契约不足以支撑分块需求

- 日期：2026-04-21
- 观察：Spring AI 的 `TextSplitter` 签名为 `String → List<String>`，只接收纯文本，丢失文档类型、解析模式、清洗信号等分块决策上下文。LangChain4j 的 7 个分块器同样全部工作在纯文本层
- 判断依据：架构设计阶段的框架能力对比分析
- 启示：Java 生态框架的分块能力整体偏弱，自建策略层是正确选择
- 状态：已验证

## [O-002] LangChain4j 不支持中文句子检测

- 日期：2026-04-21
- 观察：LangChain4j 的 `DocumentBySentenceSplitter` 使用 Apache OpenNLP，仅捆绑英文句子模型，中文完全不可用
- 判断依据：框架源码分析和官方文档确认
- 启示：中文 RAG 系统不能依赖框架的句子级分割，需要自建或绕过
- 状态：已验证

## [O-003] 文档分块参数需与 embedding 模型上下文窗口对齐

- 日期：2026-04-21
- 观察：当前分块参数按 provider 粗粒度配置（BAILIAN 800 字符、OLLAMA 400 字符），但同一 provider 下不同模型的上下文窗口可能差异很大
- 判断依据：用户反馈，模型切换后分块参数需要随之调整
- 启示：后续应在模型注册时记录 maxInputTokens，分块策略据此动态计算
- 状态：待实施

## [O-004] 父子分块（Parent-Child）在当前架构中可行性高

- 日期：2026-04-21
- 观察：`ChunkEntity` 已有 `chunkText` 字段存文本，加 `parentChunkId` 即可表达层级。策略模式支持分层包装器设计，内层策略不受影响
- 判断依据：架构设计分析，`rag-admin-document-chunking-architecture.md` 第 11.4 节
- 启示：等专项分块策略稳定后可以实施，但会增加向量化成本（child 数量翻 2-3 倍）
- 状态：已实现（2026-04-22，SemanticChunkStrategy + ParentChunkExpansionService）

## [O-005] ES 中文分词必须安装 IK 插件

- 日期：2026-04-22
- 观察：ES 默认 `standard` 分词器对中文仅做单字切分（"中华人民共和国" → "中"、"华"、"人"...），无法满足中文语义检索。需要安装 `elasticsearch-analysis-ik` 插件，索引分词用 `ik_max_word`，搜索分词用 `ik_smart`
- 判断依据：实际测试确认，ES 官方 Docker 镜像不含 IK 插件
- 启示：IK 插件版本必须与 ES 版本严格一致，升级 ES 时必须同步升级 IK。通过自定义 Dockerfile 预装插件是可靠做法
- 状态：已验证

## [O-006] RRF 融合参数 k=60 对小结果集效果不明显

- 日期：2026-04-22
- 观察：RRF 公式 `score = Σ(1/(k + rank))` 中 k=60 是业界默认值，但当单路结果少于 10 条时，融合后排序与原始排序差异很小
- 判断依据：RRF 融合单元测试验证
- 启示：小知识库场景下 HYBRID 模式收益有限，需要足够大的文档量才能体现双路召回优势
- 状态：待观察

## [O-007] 父子分块向量化成本增加但检索质量提升

- 日期：2026-04-22
- 观察：语义分块将文档切为 ~400 字符子块后做 embedding，子块数量是普通分块的 2-3 倍，但每个子块的 embedding 更精确，检索命中率更高。父块提供上下文但不参与向量化
- 判断依据：语义分块架构设计和 SemanticChunkStrategy 实现
- 启示：语义分块适合文档量大、主题变化频繁的场景，小文档建议继续用 RecursiveFallbackStrategy
- 状态：待验证（需真实数据集验证检索质量提升）

## [O-008] RAG 系统定位：人工预处理为主，系统兜底为辅

- 日期：2026-04-25
- 观察：RAG 向量知识库系统的核心价值在于对已整理好的文档做高效检索，而非万能文档处理器。企业级 RAG 落地中，文档梳理和语料清洗通常占整个项目 60-70% 工作量
- 判断依据：用户实践经验，企业级 RAG 项目共识
- 启示：
  - 系统应做的是**尽量兜底 + 清晰暴露问题**（如不可解析的图片引用给警告）
  - 系统不应替代人工整理，但应通过前置校验和准入检查引导用户提交高质量语料
  - 功能设计时优先考虑"暴露问题让用户修"而非"系统默默容错"
- 状态：已验证

## [O-009] LLM Reranking 延迟是检索链路的瓶颈

- 日期：2026-04-22
- 观察：RerankingService 调用 LLM 对候选段落逐一打分，单次请求延迟 1-3 秒，叠加在检索链路后总延迟可能超过 5 秒
- 判断依据：Reranking 架构设计和 prompt 模板
- 启示：生产环境需要限制 maxCandidates（默认 20）和 topN（默认 3），后续可考虑批量打分或模型侧优化
- 状态：待验证

## 演化记录

### [E-001] 引入运行目录作为 Agent 执行层

- 日期：2026-04-14
- 变更：引入运行目录作为 Agent 执行层
- 原因：将项目级宪法、执行细则、工程记忆和子代理职责分层维护
- 影响：顶层入口文件负责稳定原则，运行目录负责执行细则与沉淀

### [E-002] 前台独立问答前端建立

- 日期：2026-04-17
- 变更：前台独立问答前端 `rag-chat-web` 建立，后台问答入口下线
- 原因：问答应统一归前台，后台只管知识库和文档运维
- 影响：所有问答接口走 `/api/app/chat/**`，后台不再提供问答页面

### [E-003] Tavily 联网搜索接入

- 日期：2026-04-18
- 变更：Tavily 联网搜索接入，联网来源独立持久化
- 原因：增强问答能力，支持联网实时信息检索
- 影响：新增 `ChatWebSearchSourceEntity`，前台聊天支持联网开关

### [E-004] 文档清洗架构增强

- 日期：2026-04-19
- 变更：文档清洗架构增强，引入信号检测层和策略化清洗
- 原因：不同文档类型需要不同的清洗策略，硬编码策略不灵活
- 影响：新增 `DocumentSignals`、`DocumentSignalAnalyzer`、`CleanerPolicyResolver`，清洗步骤按信号动态启用

### [E-005] 文档分块重构为策略模式

- 日期：2026-04-20
- 变更：文档分块重构为策略模式
- 原因：所有文档类型使用同一段分块逻辑，无法按类型优化
- 影响：新增 `DocumentChunkStrategy` 接口 + `RecursiveFallbackStrategy`，策略选择器按优先级匹配

### [E-006] 补充 OcrNoiseCleaner 和 LineMergeCleaner

- 日期：2026-04-20
- 变更：补充 OcrNoiseCleaner 和 LineMergeCleaner
- 原因：清洗层有策略定义但无 CleanerStep 实现
- 影响：清洗层五个步骤全部实现完毕

### [E-007] 实现四种专项分块策略

- 日期：2026-04-21
- 变更：实现四种专项分块策略（Markdown/Html/PdfOcr/PdfText）
- 原因：通用策略不感知文档结构，分块质量不够
- 影响：ChunkContext 增加 parseMode，新增 4 个策略 + 30 个测试，RAG 文档处理三块（加载/清洗/分块）全部完成

### [E-008] 完成 memory 沉淀，清理已完成 plans

- 日期：2026-04-21
- 变更：完成 memory 沉淀，清理已完成 plans
- 原因：6 个 plans 已完成，长期结论需回写到 architectures
- 影响：memory 五个文件补齐核心内容，6 个 plans 删除，架构文档更新

### [E-009] 子代理路由规则增加并行执行判断策略

- 日期：2026-04-21
- 变更：子代理路由规则增加并行执行判断策略
- 原因：用户要求主 Agent 自主判断任务是否适合并行开发，无需用户额外指示
- 影响：`subagent-routing.md` 增加并行判断章节，`learned-rules.md` 记录 R-007

### [E-010] 实现 PG/Milvus/ES 三端数据同步

- 日期：2026-04-22
- 变更：实现 PG/Milvus/ES 三端数据同步，修复 Milvus 向量孤立问题
- 原因：文档删除和重解析时 Milvus 向量未被清理；ES 已配置 Docker 但无 Java 集成
- 影响：新建 ElasticsearchProperties + ElasticsearchClient + ChunkSearchSyncService；MilvusVectorStoreClient 新增 delete；DocumentParseProcessor 新增 SYNC_SEARCH_ENGINE 步骤；DocumentService/KnowledgeBaseService delete 接入三端清理；SystemHealthService 增加 ES 健康检查

### [E-011] 自定义 agent 角色迁移

- 日期：2026-04-22
- 变更：自定义 agent 角色从 `.claude/subagents`（概念文档）迁移到 `.claude/agents`（可被 Agent 工具调用）
- 原因：planner/executor/verifier 是软件工程固定角色，应注册为持久化自定义 agent
- 影响：新建 `.claude/agents/planner.md`、`executor.md`、`verifier.md`，删除旧 `.claude/subagents/` 目录，CLAUDE.md 更新引用

### [E-012] RAG 检索全链路补全

- 日期：2026-04-22
- 变更：RAG 检索全链路补全（ES 关键词检索 + RRF 融合 + 混合检索 + 查询改写 + Reranking + 语义分块 + 父子分块）
- 原因：检索层仅有向量单路召回，缺少关键词检索、融合排序、查询增强和精排能力；分块层缺少语义感知
- 影响：检索模式新增 SEMANTIC_ONLY/KEYWORD_ONLY/HYBRID 三种；新增 KeywordRetrievalStrategy、RrfFusionService、QueryRewritingService（Multi-Query + HyDE）、RerankingService、SemanticChunkStrategy、ParentChunkExpansionService；4 次 git commit，320 个测试全部通过
- 关键提交：102e5c5（关键词+RRF+混合）、9efae8a（查询改写）、8f2438e（Reranking）、3e1816f（语义分块+父子块）

### [E-013] SessionStart hook 调整为轻量加载

- 日期：2026-04-25
- 变更：SessionStart hook 从加载完整 `project-progress.md` 调整为加载轻量 `session-brief.md`
- 原因：新会话需要知道宏观进度，但不应在简单任务中强制注入完整进度明细占用上下文
- 影响：`project-progress.md` 保持完整进度事实源，中等及以上任务和重大功能收口时按需读取；重大功能完成后需同步更新 `project-progress.md` 与 `session-brief.md`
