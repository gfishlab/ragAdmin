# Tavily 联网搜索接入设计

## 1. 背景

当前前台问答链路已经支持：

- 用户在前台会话中开启或关闭联网
- `AppChatService` 通过 `ChatExecutionPlanningService` 判断是否需要联网
- 通过 `WebSearchProvider` 抽象拼接联网摘要到 prompt
- 未配置真实 Provider 时回退到 `NoopWebSearchProvider`

现阶段缺口是：

- 还没有真实联网搜索 Provider
- 联网调用缺少更完整的日志、失败降级、结果裁剪和健康可观测能力

本次目标是在不破坏既有问答编排边界的前提下，把 Tavily 接入为真实联网搜索来源，并补齐可观测与留痕能力。

## 2. 目标与非目标

### 2.1 目标

- 前台用户开启联网后，知识库问答和通用问答可以通过 Tavily 获取真实联网摘要
- 保持 `infra.search` 作为联网搜索适配层，不让 Tavily 细节渗透到 `app`、`chat`、`retrieval` 领域逻辑
- 增加结构化日志，记录联网是否触发、搜索词、耗时、结果条数、降级原因
- 增加失败降级策略，确保 Tavily 不可用时主问答链路仍能继续
- 增加结果裁剪能力，防止联网结果过长挤占知识库上下文
- 在系统健康检查中暴露 Tavily 配置与可用状态，便于排障

### 2.2 非目标

- 不做多 Provider 动态切换
- 不做数据库级联网调用审计表
- 不改前端交互与接口契约
- 不把后台 `ChatService` 也一并改造成支持联网

## 3. 设计原则

- 保持现有 `WebSearchProvider` 抽象不变，最小化对调用方的入侵
- 真实联网调用只放在基础设施层
- 敏感配置仅放本地 secret 文件或私有环境变量，不进入公共配置
- 日志留痕优先使用结构化日志，不记录 API Key 等敏感值
- 联网增强始终是“补充能力”，不能让失败扩散为主链路失败

## 4. 架构方案

### 4.1 组件划分

新增或调整以下组件：

- `WebSearchProperties`
  - 统一管理联网搜索配置，包括是否启用、默认返回条数、日志截断和上下文裁剪参数
- `TavilyProperties`
  - 管理 Tavily 专属配置，包括 `baseUrl`、`apiKey`、`timeoutSeconds`、`searchDepth`、`topic`、`maxResults`
- `WebSearchConfiguration`
  - 负责注册 `WebSearchProvider` Bean
  - 当 Tavily 配置完整时注入 `TavilyWebSearchProvider`
  - 否则回退 `NoopWebSearchProvider`
- `TavilyWebSearchProvider`
  - 使用 `RestClient` 调用 Tavily 搜索接口
  - 做请求超时、状态码处理、响应映射、日志留痕和结果裁剪

### 4.2 调用链路

前台问答保持原有流程：

1. 前台发送 `webSearchEnabled=true`
2. `AppChatService` 根据 `webSearchProvider.isAvailable()` 计算当前是否具备联网能力
3. `ChatExecutionPlanningService` 决定是否需要联网，以及使用什么 `webSearchQuery`
4. `AppChatService` 调用 `webSearchProvider.search(query, topK)`
5. 返回的 `WebSearchSnippet` 被拼装为“联网搜索摘要”
6. 知识库 prompt 中继续保持“知识片段优先，联网摘要仅作补充”的约束

## 5. 失败降级与异常处理

Tavily 失败时统一降级为空结果，不中断主问答链路。降级触发场景包括：

- API Key 缺失
- Tavily 被显式禁用
- HTTP 超时
- HTTP 4xx / 5xx
- 响应结构缺字段或反序列化失败

降级策略：

- `TavilyWebSearchProvider.isAvailable()` 返回 `false` 时，前台显示“当前环境未接入真实联网搜索能力”
- `search()` 运行期异常由 `AppChatService` 兜底为空列表
- 在 provider 内部记录 `warn` 级日志，包含失败类型、耗时、查询摘要和结果条数

## 6. 日志、可观测与留痕

### 6.1 结构化日志

联网日志至少覆盖：

- 是否启用联网
- 实际搜索 query 的长度与脱敏摘要
- Tavily 调用耗时
- 返回条数
- 裁剪前后条数
- 是否降级
- 降级原因分类

日志约束：

- 不记录 API Key
- 不完整打印长问题全文，默认裁剪到安全长度
- 响应正文不直接整段落日志

### 6.2 健康检查

在 `SystemHealthService` 中新增 Tavily 健康项：

- `UNKNOWN`：未启用或未配置 API Key
- `UP`：配置完整且真实 Provider 已装配
- `DOWN`：配置完整但 Provider 未就绪

本次不在健康检查里主动调用 Tavily 远端接口，避免把管理端健康轮询变成额外的联网请求与速率消耗。运行期成功、超时和异常通过结构化日志补足观察面。

### 6.3 留痕范围

本次先做服务端结构化日志留痕，不新增数据库审计表。这样可以先满足排障、验收和运维观察，避免本轮改动面继续扩大。

## 7. 结果裁剪策略

为了避免联网摘要占满 prompt，需要做多层裁剪：

- 裁剪返回条数
- 裁剪每条标题长度
- 裁剪每条摘要长度
- 裁剪拼接后的总上下文长度

裁剪目标：

- 尽量保留来源链接与标题
- 优先保留前几条高相关结果
- 超限时丢弃低优先级结果，而不是简单把长文本整段截到不可读

## 8. 配置方案

共享配置新增：

- `rag.search.enabled`
- `rag.search.default-top-k`
- `rag.search.context-max-chars`
- `rag.search.log-query-max-chars`
- `rag.search.result-title-max-chars`
- `rag.search.result-snippet-max-chars`

本地 secret 文件新增：

- `rag.search.tavily.base-url`
- `rag.search.tavily.api-key`
- `rag.search.tavily.timeout-seconds`
- `rag.search.tavily.search-depth`
- `rag.search.tavily.topic`
- `rag.search.tavily.max-results`

## 9. 测试策略

重点补以下测试：

- `TavilyWebSearchProvider` 在配置缺失时不可用
- `TavilyWebSearchProvider` 能正确映射正常响应
- `TavilyWebSearchProvider` 能对结果做裁剪
- `AppChatService` 在联网异常时继续降级为空结果
- `AppPortalService` 能正确暴露 Tavily 可用状态
- `SystemHealthService` 能返回 Tavily 的健康状态

## 10. 决策结论

本次采用“单 Provider 接入 Tavily + 增强日志/降级/裁剪/健康可见性”的方案：

- 满足真实联网问答需求
- 保留当前 `WebSearchProvider` 抽象边界
- 避免一次性引入多 Provider 复杂度
- 为后续继续扩展供应商保留基本接口兼容性
