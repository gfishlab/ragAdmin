# 联网来源独立持久化与展示 Implementation Plan

> **给执行型 Agent：** 当前环境不启用子代理流程，直接按本计划在当前会话内执行。步骤使用 `- [ ]` 复选框语法跟踪。

**Goal:** 为前台问答新增联网来源独立持久化、接口返回与前端折叠展示能力，并保证历史消息可回放。

**Architecture:** 后端沿用现有 `AppChatService -> ChatExchangePersistenceService` 链路，在 `chat_message` 旁新增 `chat_web_search_source` 关系表；接口通过 `webSearchSources` 独立返回；前端在消息卡片下方新增“联网来源”折叠区，与知识库引用分开展示。

**Tech Stack:** Spring Boot 3、MyBatis Plus、Flyway、Vue 3、TypeScript、Element Plus、JUnit 5

---

## 文件结构

- 新增 `rag-admin-server/src/main/resources/db/migration/V12__add_chat_web_search_source.sql`
  - 建表并加索引
- 新增 `rag-admin-server/src/main/java/com/ragadmin/server/chat/entity/ChatWebSearchSourceEntity.java`
  - 联网来源实体
- 新增 `rag-admin-server/src/main/java/com/ragadmin/server/chat/mapper/ChatWebSearchSourceMapper.java`
  - 联网来源 Mapper
- 新增 `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/WebSearchSourceResponse.java`
  - 对外返回结构
- 修改 `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatResponse.java`
  - 增加 `webSearchSources`
- 修改 `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatMessageResponse.java`
  - 增加 `webSearchSources`
- 修改 `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatStreamEventResponse.java`
  - `COMPLETE` 事件透传 `webSearchSources`
- 修改 `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatExchangePersistenceService.java`
  - 持久化/替换/映射联网来源
- 修改 `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java`
  - 执行上下文带出联网来源并在历史消息中查询回填
- 修改 `rag-admin-server/src/test/java/com/ragadmin/server/chat/service/ChatExchangePersistenceServiceTest.java`
  - 补持久化单测
- 修改 `rag-admin-server/src/test/java/com/ragadmin/server/web/AppApiWebMvcTest.java`
  - 补接口断言
- 修改 `rag-chat-web/src/types/chat.ts`
  - 增加 `WebSearchSource` 类型和相关字段
- 修改 `rag-chat-web/src/api/chat.ts`
  - 处理同步、历史和流式返回中的 `webSearchSources`
- 修改 `rag-chat-web/src/components/chat/AppChatWorkspace.vue`
  - 增加联网来源折叠状态、按钮和展示块
- 修改 `docs/rag-admin-api-design.md`
  - 补接口字段说明
- 修改 `docs/rag-admin-schema-v1.sql`
  - 补 `chat_web_search_source`

## Chunk 1: 后端数据模型与持久化

### Task 1: 新增数据库结构

**Files:**
- Create: `rag-admin-server/src/main/resources/db/migration/V12__add_chat_web_search_source.sql`
- Modify: `docs/rag-admin-schema-v1.sql`

- [ ] **Step 1: 编写 migration**
  - 建表 `chat_web_search_source`
  - 增加外键和两个索引

- [ ] **Step 2: 同步 SQL 草案文档**
  - 在 `chat_answer_reference` 后补充 `chat_web_search_source`
  - 保持命名与 migration 一致

- [ ] **Step 3: 检查字段可空策略**
  - `message_id`、`rank_no` 非空
  - `title`、`source_url`、`published_at`、`snippet` 按标准化结果允许为空

### Task 2: 新增实体、Mapper 与 DTO

**Files:**
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/chat/entity/ChatWebSearchSourceEntity.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/chat/mapper/ChatWebSearchSourceMapper.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/WebSearchSourceResponse.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatResponse.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatMessageResponse.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/dto/ChatStreamEventResponse.java`

- [ ] **Step 1: 新建实体和 Mapper**
- [ ] **Step 2: 新建 `WebSearchSourceResponse`**
- [ ] **Step 3: 为 `ChatResponse` 增加 `List<WebSearchSourceResponse> webSearchSources`**
- [ ] **Step 4: 为 `ChatMessageResponse` 增加 `List<WebSearchSourceResponse> webSearchSources`**
- [ ] **Step 5: 为 `ChatStreamEventResponse` 增加 `webSearchSources` 并调整 `complete(...)`**

### Task 3: 扩展持久化服务

**Files:**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/chat/service/ChatExchangePersistenceService.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/chat/service/ChatExchangePersistenceServiceTest.java`

- [ ] **Step 1: 注入 `ChatWebSearchSourceMapper`**
- [ ] **Step 2: 扩展 `persistExchange(...)`，接收 `List<WebSearchSnippet>`**
- [ ] **Step 3: 扩展 `replaceExchange(...)`，删除旧来源并重建**
- [ ] **Step 4: 增加 `persistWebSearchSources(...)` 和映射方法**
- [ ] **Step 5: 在 `buildChatResponse(...)` 中返回 `webSearchSources`**
- [ ] **Step 6: 补单测覆盖新增来源写入与替换删除逻辑**

Run:
`mvn -pl rag-admin-server -Dtest=ChatExchangePersistenceServiceTest test`

Expected:
`PASS`

## Chunk 2: 后端问答链路与接口返回

### Task 4: 让问答执行上下文携带联网来源

**Files:**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java`

- [ ] **Step 1: 给 `PreparedChatExecution` 增加 `List<WebSearchSnippet> webSearchSnippets`**
- [ ] **Step 2: `prepareChatExecution(...)` 返回时带出 `webSearchSnippets`**
- [ ] **Step 3: 同步问答调用 `persistExchange(...)` 时传递 `execution.webSearchSnippets()`**
- [ ] **Step 4: 流式完成时传递 `execution.webSearchSnippets()`**
- [ ] **Step 5: 重新生成回答时传递 `execution.webSearchSnippets()`**

### Task 5: 历史消息查询回填联网来源

**Files:**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/web/AppApiWebMvcTest.java`

- [ ] **Step 1: 在 `listMessages(...)` 中批量查询 `chat_web_search_source`**
- [ ] **Step 2: 按 `messageId` 分组并按 `rankNo` 排序**
- [ ] **Step 3: 构造 `ChatMessageResponse.webSearchSources`**
- [ ] **Step 4: 补同步历史消息接口断言**
- [ ] **Step 5: 补同步问答接口和流式完成事件断言**

Run:
`mvn -pl rag-admin-server -Dtest=AppApiWebMvcTest test`

Expected:
`PASS`

## Chunk 3: 前端类型、接口映射与界面

### Task 6: 补齐前端类型与接口映射

**Files:**
- Modify: `rag-chat-web/src/types/chat.ts`
- Modify: `rag-chat-web/src/api/chat.ts`

- [ ] **Step 1: 增加 `WebSearchSource` 类型**
- [ ] **Step 2: 为 `ChatExchange`、`ChatResponse`、`ChatStreamEvent` 增加 `webSearchSources`**
- [ ] **Step 3: 历史消息接口映射 `webSearchSources`**
- [ ] **Step 4: 同步问答和流式事件统一归一化 `webSearchSources`**

### Task 7: 增加独立折叠区展示

**Files:**
- Modify: `rag-chat-web/src/components/chat/AppChatWorkspace.vue`

- [ ] **Step 1: 组件地图确认**
  - `AppChatWorkspace.vue` 继续负责会话消息编排和折叠状态
  - 不新增子组件，避免为了单个来源块扩散复杂度

- [ ] **Step 2: 增加展开状态**
  - 新增 `expandedWebSearchSourceMessageIds`
  - 新增 `isWebSearchSourceExpanded(...)`
  - 新增 `toggleWebSearchSourcePanel(...)`

- [ ] **Step 3: 更新回答完成后的本地消息合并逻辑**
  - 同步消息、流式完成消息、历史消息都带上 `webSearchSources`

- [ ] **Step 4: 更新模板**
  - 在“查看引用”旁新增“查看联网来源”
  - 仅当 `webSearchSources.length > 0` 时显示入口
  - 新增独立折叠区，显示标题、链接、发布时间、摘要

- [ ] **Step 5: 更新样式**
  - 与 `reference-block` 风格保持一致
  - 但类名独立，避免知识库引用和联网来源混用样式语义

Run:
`npm run build`

Expected:
`构建通过，无 TypeScript 错误`

## Chunk 4: 文档、联调与收口

### Task 8: 同步接口文档

**Files:**
- Modify: `docs/rag-admin-api-design.md`

- [ ] **Step 1: 为历史消息接口增加 `webSearchSources` 说明**
- [ ] **Step 2: 为同步问答响应增加 `webSearchSources` 说明**
- [ ] **Step 3: 为流式完成事件增加 `webSearchSources` 说明**

### Task 9: 本地验证与收口

**Files:**
- Modify: `docs/plans/2026-03-24-chat-web-search-source-design.md`
- Modify: `docs/plans/2026-03-24-chat-web-search-source-implementation-plan.md`

- [ ] **Step 1: 运行后端定向测试**
  Run:
  `mvn -pl rag-admin-server -Dtest=ChatExchangePersistenceServiceTest,AppApiWebMvcTest test`
  Expected:
  `PASS`

- [ ] **Step 2: 运行前端构建**
  Run:
  `npm run build`
  Expected:
  `PASS`

- [ ] **Step 3: 联调验证**
  - 开启联网提问一轮
  - 确认回答下方出现“查看联网来源”
  - 刷新页面后重新进入会话，确认来源仍可展开查看

- [ ] **Step 4: Git 收口**
  Run:
  `git status --short`
  `git add ...`
  `git commit -m "feat: 持久化前台问答联网来源并独立展示"`
  `git push origin master`

Plan complete and saved to `docs/plans/2026-03-24-chat-web-search-source-implementation-plan.md`。接下来直接按计划实现。
