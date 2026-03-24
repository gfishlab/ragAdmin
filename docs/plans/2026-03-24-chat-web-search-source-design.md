# 联网来源独立持久化与展示设计

## 1. 背景

当前前台问答已经支持 Tavily 真实联网搜索，服务端也会把联网结果裁剪后注入 prompt。

但当前仍有两个明显缺口：

- 联网结果只参与生成，不会作为回答来源持久化
- 前端只能展示知识库引用 `references`，不能把联网网页来源单独展示出来

这会带来三个问题：

- 刷新页面或重新进入历史会话后，看不到本轮回答实际参考了哪些联网网页
- 联网来源和知识库引用混在同一语义层次，用户无法区分“知识库证据”和“联网补充证据”
- 无法满足答复留痕、验收回放和问题排查需求

## 2. 目标与非目标

### 2.1 目标

- 联网开启且实际发生联网搜索时，持久化本轮回答关联的网页来源
- 联网来源与知识库引用分开建模、分开返回、分开展示
- 同步问答、流式问答、重新生成回答三条链路返回一致字段
- 历史消息查询可回放联网来源，前端支持展开/收起
- 展示字段固定为：标题、网址、发布时间、摘要

### 2.2 非目标

- 不改 Tavily Provider 自身的搜索逻辑
- 不做知识库引用结构重构
- 不做新的独立联网审计后台页面
- 不把联网来源混入 `chat_message` 的 JSON 字段中

## 3. 方案对比

### 3.1 方案 A：只在前端临时保留联网来源

优点：

- 改动最小
- 不需要数据库变更

缺点：

- 页面刷新后丢失
- 无法回放历史问答
- 不满足留痕要求

### 3.2 方案 B：把联网来源塞进 `chat_message` 扩展字段

优点：

- 表数量不增加
- 查询链路简单

缺点：

- 结构不清晰
- 不利于排序、过滤和后续扩展
- 与当前 `chat_answer_reference` 的关系型设计风格不一致

### 3.3 方案 C：新增独立表 `chat_web_search_source`

优点：

- 与知识库引用职责边界清晰
- 持久化结构稳定，便于历史回放和后续治理
- 能与现有 `chat_answer_reference` 模式保持一致

缺点：

- 需要新增 migration、实体、Mapper 和查询拼装逻辑

## 4. 最终选择

本次采用方案 C。

核心决策如下：

- 新增表 `chat_web_search_source`
- 以 `message_id` 关联 `chat_message`
- 每条联网来源按 `rank_no` 落库，保留 Tavily 返回顺序
- 对外接口新增独立字段 `webSearchSources`
- 前端把“知识库引用”和“联网来源”分别做成两个折叠区

## 5. 数据模型设计

新增表结构如下：

- `id`：主键
- `message_id`：所属回答消息 ID
- `title`：网页标题
- `source_url`：来源网址
- `published_at`：发布时间，可为空
- `snippet`：摘要
- `rank_no`：来源顺序
- `created_at`：创建时间
- `updated_at`：更新时间

索引策略：

- `idx_chat_web_search_source_message_id`
- `idx_chat_web_search_source_message_rank`

约束原则：

- `message_id` 必须外键关联 `chat_message`
- `title/source_url/snippet` 允许按当前 Provider 标准化结果存储，空值使用 `NULL`
- 不额外存储原始 Tavily 响应 JSON，避免放大冗余和敏感风险

## 6. 服务端链路设计

### 6.1 持久化链路

`AppChatService.prepareChatExecution(...)` 当前已经拿到 `List<WebSearchSnippet>`，但只用于 prompt 构建。

本次调整为：

1. `prepareChatExecution(...)` 把 `webSearchSnippets` 放入 `PreparedChatExecution`
2. `chat(...)` / `streamChat(...)` / `regenerateMessage(...)` 调用持久化服务时，把 `webSearchSnippets` 一起传入
3. `ChatExchangePersistenceService` 新增联网来源持久化逻辑
4. `replaceExchange(...)` 重新生成回答时，先删除旧来源，再写入新来源

### 6.2 查询回放链路

`AppChatService.listMessages(...)` 在查询历史消息时：

1. 批量加载 `chat_answer_reference`
2. 批量加载 `chat_web_search_source`
3. 分别按 `message_id` 分组
4. 回填到 `ChatMessageResponse.references` 与 `ChatMessageResponse.webSearchSources`

### 6.3 DTO 设计

新增 DTO：

- `WebSearchSourceResponse`

调整 DTO：

- `ChatResponse`
- `ChatMessageResponse`
- `ChatStreamEventResponse`

字段命名统一使用：

- `webSearchSources`

这样可以保证同步返回、流式完成事件、历史列表三种返回结构保持一致语义。

## 7. 前端展示设计

前端继续沿用当前 `AppChatWorkspace.vue` 的单组件编排方式，本次不额外拆组件，避免只为一个折叠区引入过度抽象。

组件边界保持：

- `AppChatWorkspace.vue` 负责消息列表编排与折叠状态管理
- `ChatMarkdownContent.vue` 继续只负责答案正文渲染

新增状态：

- `expandedWebSearchSourceMessageIds`

新增交互：

- 回答下方增加“查看联网来源 / 收起联网来源”按钮
- 联网来源块默认收起
- 历史消息、同步回答、流式完成回答统一使用同一渲染逻辑

展示字段：

- 标题
- 网址，点击新窗口打开
- 发布时间
- 摘要

展示原则：

- 与知识库引用块视觉上保持同一设计语言
- 但文案和区块标题明确区分，不与知识库引用混淆

## 8. 错误处理与边界

- 未开启联网或本轮未触发联网时，`webSearchSources` 返回空数组
- 联网失败降级为空结果时，不创建来源记录
- 历史消息查询不到来源记录时，前端不显示“查看联网来源”入口
- 重新生成回答后，只保留最新回答对应的联网来源，不保留旧版本

## 9. 测试策略

后端重点补：

- `ChatExchangePersistenceServiceTest`
  - 校验新增来源写入
  - 校验重新生成时会删除并重建来源
- `AppApiWebMvcTest`
  - 校验同步接口返回 `webSearchSources`
  - 校验流式完成事件带出 `webSearchSources`

前端至少完成：

- 类型与接口映射正确
- 本地构建通过
- 联网页面手工验证展开/收起和历史回放

## 10. 决策结论

本次采用“独立表持久化 + 接口独立字段返回 + 前端独立折叠区展示”的完整方案。

这样可以同时满足：

- 联网来源与知识库引用语义隔离
- 历史消息可回放
- 回答留痕可追踪
- 不破坏现有 Tavily Provider 与问答编排边界
