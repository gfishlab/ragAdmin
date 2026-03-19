# 后台权限边界与在线会话治理设计

## 1. 背景

当前项目已经明确以下方向：

- 统一用户源，不拆分前后台两套账号体系
- 后端认证与权限校验统一基于 `sa-token`
- `sa-token` 必须结合 Redis 承载登录态、在线会话热数据与强制下线能力
- 用户聊天历史、知识库内会话与消息事实数据必须持久化在 PostgreSQL

与此同时，当前后台权限控制仍停留在较粗粒度阶段，在线会话治理和强制下线能力也尚未形成统一设计，因此需要把认证授权、权限矩阵、在线会话治理和会话持久化边界一次性收口。

## 2. 目标

本轮设计目标如下：

- 明确后台认证授权统一走 `sa-token + Redis`
- 明确前后台登录态按 `loginType` 隔离
- 明确后台权限码矩阵与页面、接口映射范围
- 明确在线会话治理与按 `userId` 强制下线边界
- 明确登录态热数据与聊天历史持久化数据的存储职责划分

## 3. 技术基线

### 3.1 认证与授权

- 后端认证与授权统一使用 `sa-token`
- PostgreSQL 继续作为用户、角色、权限事实来源
- Redis 作为 `sa-token` 登录态、在线会话热数据和强制下线能力的存储载体
- 后端接口权限校验统一优先采用 `sa-token` 注解与统一权限服务

### 3.2 登录态隔离

前后台继续共享统一用户源，但登录态必须隔离：

- 后台管理端：`loginType=admin`
- 问答前台：`loginType=app`

这样可以在不拆账号体系的前提下，保证前后台 Token、在线状态、退出登录和强制下线范围相互独立。

## 4. 权限码矩阵

本轮后台权限码固定为以下集合：

- `DASHBOARD_VIEW`
- `CHAT_CONSOLE_ACCESS`
- `KB_MANAGE`
- `MODEL_MANAGE`
- `TASK_VIEW`
- `TASK_OPERATE`
- `AUDIT_VIEW`
- `STATISTICS_VIEW`
- `USER_MANAGE`

角色与权限矩阵建议如下：

- `ADMIN`：拥有全部权限
- `KB_ADMIN`：`DASHBOARD_VIEW`、`CHAT_CONSOLE_ACCESS`、`KB_MANAGE`、`MODEL_MANAGE`、`TASK_VIEW`、`TASK_OPERATE`、`STATISTICS_VIEW`
- `AUDITOR`：`DASHBOARD_VIEW`、`TASK_VIEW`、`AUDIT_VIEW`、`STATISTICS_VIEW`
- `APP_USER`：不进入后台，不参与后台权限矩阵

## 5. 页面与接口映射

### 5.1 后台页面

- `/dashboard` 对应 `DASHBOARD_VIEW`
- `/chat` 对应 `CHAT_CONSOLE_ACCESS`
- `/knowledge-bases`、`/knowledge-bases/create`、`/knowledge-bases/:id`、`/knowledge-bases/:id/edit`、`/documents/:id` 对应 `KB_MANAGE`
- `/models` 对应 `MODEL_MANAGE`
- `/tasks`、`/tasks/:id` 对应 `TASK_VIEW`
- `/audit-logs` 对应 `AUDIT_VIEW`
- `/vector-indexes` 对应 `STATISTICS_VIEW`
- `/users` 对应 `USER_MANAGE`

### 5.2 后台接口

- `/api/admin/chat/**` 对应 `CHAT_CONSOLE_ACCESS`
- `/api/admin/knowledge-bases/**`、`/api/admin/documents/**`、`/api/admin/files/**` 对应 `KB_MANAGE`
- `/api/admin/models/**`、`/api/admin/model-providers/**` 对应 `MODEL_MANAGE`
- `/api/admin/tasks`、`/api/admin/tasks/{id}`、`/api/admin/tasks/summary`、`/api/admin/events/tasks` 对应 `TASK_VIEW`
- `/api/admin/tasks/{id}/retry` 对应 `TASK_OPERATE`
- `/api/admin/audit-logs` 对应 `AUDIT_VIEW`
- `/api/admin/statistics/**` 对应 `STATISTICS_VIEW`
- `/api/admin/users/**`、`/api/admin/user-sessions/**` 对应 `USER_MANAGE`

### 5.3 按钮级控制范围

本轮只对高风险操作做按钮级控制：

- 删除知识库
- 上传文档、批量解析、批量删除、重试
- 删除模型、探活、编辑模型
- 任务重试
- 用户新增、编辑、角色分配、强制下线

本轮不做字段级、列级、数据行级和组织树级权限控制。

## 6. 在线会话治理

### 6.1 治理粒度

首期在线会话治理只按 `userId` 处理，不做设备粒度治理。

- 不返回设备、浏览器、单 Token 明细
- 不支持按设备逐个踢下线
- 强制下线最小管理粒度为 `userId`

### 6.2 强制下线范围

虽然不按设备治理，但仍然保留系统入口维度的作用范围：

- `admin`
- `app`
- `all`

作用范围不是设备维度，而是登录域维度，用于区分后台管理端和问答前台。

### 6.3 接口建议

- `GET /api/admin/user-sessions`
- `GET /api/admin/user-sessions/{userId}`
- `POST /api/admin/user-sessions/{userId}/kickout`

其中强制下线请求体建议至少包含：

- `scope`
- `reason`

### 6.4 审计要求

每次强制下线都必须写审计日志，至少记录：

- `operatorUserId`
- `targetUserId`
- `scope`
- `reason`
- `operateTime`

## 7. 登录态热数据与聊天历史边界

### 7.1 Redis 承担的职责

Redis 只承载认证授权层的热数据：

- 登录 Token
- `sa-token` Session
- 在线状态
- 强制下线标记
- 最近活跃时间
- 按 `userId` 的在线索引

### 7.2 PostgreSQL 承担的职责

PostgreSQL 承担问答业务事实数据：

- 聊天会话主表
- 会话与知识库关系
- 消息明细
- 用户问题与模型回答
- 引用片段
- 反馈记录
- 必要的上下文摘要与审计字段

### 7.3 会话隔离原则

统一用户源不等于共享同一聊天上下文，以下场景必须隔离：

- 首页通用问答
- 知识库内问答
- 后台管理端问答

聊天历史查询、追问、删除和导出都必须显式校验 `userId` 归属。

## 8. 迁移与验收边界

建议按以下顺序推进：

1. 接入 `sa-token` 与 Redis 基础设施
2. 收口前后台登录与刷新接口
3. 收口后台权限注解、菜单、路由和高风险按钮
4. 增加在线会话治理与按 `userId` 强制下线
5. 退场旧的自定义 `JWT + Interceptor + 手写权限判断`

验收重点包括：

- `ADMIN` 能登录后台，也能登录前台
- `APP_USER` 只能登录前台，不能登录后台
- 后台 `/api/admin/auth/me` 正确返回 `roles + permissions`
- 无权限后台接口被 `sa-token` 拒绝
- 强制下线指定 `userId` 后，对应登录域请求立即失效
- 强制下线操作产生审计记录
- Redis 承担登录态热数据，PostgreSQL 继续承担聊天历史事实数据
