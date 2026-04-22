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
- 状态：远期规划

## [O-005] ES 中文分词必须安装 IK 插件

- 日期：2026-04-22
- 观察：ES 默认 `standard` 分词器对中文仅做单字切分（"中华人民共和国" → "中"、"华"、"人"...），无法满足中文语义检索。需要安装 `elasticsearch-analysis-ik` 插件，索引分词用 `ik_max_word`，搜索分词用 `ik_smart`
- 判断依据：实际测试确认，ES 官方 Docker 镜像不含 IK 插件
- 启示：IK 插件版本必须与 ES 版本严格一致，升级 ES 时必须同步升级 IK。通过自定义 Dockerfile 预装插件是可靠做法
- 状态：已验证
