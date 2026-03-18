# ragAdmin 后端目录结构规范

## 1. 定位

当前 `rag-admin-server` 不采用传统的全局平铺结构：

- `controller`
- `service`
- `impl`
- `mapper`
- `config`

而是采用“顶层按业务域拆分，域内保留小分层”的模块化单体结构。

这不是严格战术 DDD，但明显具备以下倾向：

- 按业务边界组织代码，而不是按技术层整体堆叠
- 将外部依赖适配下沉到 `infra`
- 将检索编排、文档解析、任务流转等复杂流程从普通 CRUD 中抽离

本规范用于约束后续新增代码的目录归属，避免项目逐步退化为混合式无边界结构。

## 2. 顶层模块

后端主包固定按业务域组织：

```text
com.ragadmin.server
├─ audit
├─ auth
├─ chat
├─ common
├─ document
├─ infra
├─ internal
├─ knowledge
├─ model
├─ retrieval
├─ statistics
├─ system
└─ task
```

### 2.1 各模块职责

`auth`
- 认证、授权、用户、角色、Token、登录态相关能力

`model`
- 模型提供方、模型定义、能力映射、探活、默认模型解析

`knowledge`
- 知识库主实体、知识库配置、知识库级管理能力

`document`
- 文档上传、版本、切片、解析任务触发、OCR、向量化前后处理

`retrieval`
- RAG 检索编排、召回、上下文组织、检索参数配置

`chat`
- 问答会话、消息、引用片段、反馈

`task`
- 任务列表、任务详情、任务步骤、重试记录、实时事件推送

`audit`
- 操作审计、请求审计、审计落库与查询

`statistics`
- 聚合统计、报表类查询、概览数据接口

`system`
- 系统健康检查、环境依赖状态、系统级管理能力

`internal`
- 内部回调、内部接口、仅服务间使用的入口

`infra`
- 外部依赖适配层，例如 AI、对象存储、向量库、OCR、外部 HTTP/RPC

`common`
- 跨模块公共能力，例如统一响应、全局异常、基础配置、通用模型

## 3. 域内小分层

业务域内部默认采用如下小分层：

```text
<domain>
├─ controller
├─ dto
├─ entity
├─ mapper
└─ service
```

这是本项目的默认结构，不需要强行增加 `impl` 层。

### 3.1 默认子包职责

`controller`
- 只负责 HTTP 入口、参数接收、响应返回、调用服务
- 不承载复杂业务编排

`dto`
- 只放当前业务域的请求对象、响应对象、列表项对象、聚合展示对象
- 不把 DTO 抽到全局公共目录

`entity`
- 放数据库表对应的持久化实体
- 当前阶段允许 `entity` 直接作为 MyBatis Plus 实体使用

`mapper`
- 放数据库访问接口
- 允许少量聚合查询 SQL
- 不承载业务规则

`service`
- 放当前业务域的主要业务逻辑
- 不拆 `service/impl`

## 4. 可选专项子包

只有在当前业务域存在明显专项职责时，才允许继续细分子包。

### 4.1 已允许且推荐的专项子包

`document.parser`
- 文档解析、OCR、调度器、后台处理器、解析阶段性流程

`document.support`
- 文档域辅助服务、描述符、向量化支撑对象

`auth.config`
- 鉴权相关配置、拦截器装配、认证属性

`auth.model`
- 认证上下文模型、Token 声明模型等非数据库实体

`retrieval.config`
- 检索链路独立配置

`infra.ai`
- AI 平台接入与适配

`infra.storage`
- MinIO 或其他对象存储适配

`infra.vector`
- Milvus 或其他向量存储适配

`infra.health`
- 外部依赖存活检查的适配支撑

### 4.2 什么时候允许新增专项子包

满足以下任一条件时可以新增：

- 存在完整独立子流程，例如解析流水线、检索编排、回调处理
- 存在明显外部依赖适配职责
- 单个域内文件数量明显增加，原始五层结构已经不足以表达职责

不满足以上条件时，优先放回当前域的 `service`、`dto` 或 `mapper`。

## 5. 明确禁止

禁止新增以下全局平铺目录：

```text
com.ragadmin.server.controller
com.ragadmin.server.service
com.ragadmin.server.service.impl
com.ragadmin.server.mapper
com.ragadmin.server.entity
com.ragadmin.server.dto
```

同时禁止以下做法：

- 新增 `service/impl` 双层样板结构
- 将某个业务域的 DTO、Entity、Mapper 抽到全局公共包
- 将第三方 SDK 直接扩散到业务域 `service`
- 将跨模块编排逻辑写进 `controller`
- 把业务相关工具类持续堆入 `common`

## 6. 新增代码放置规则

### 6.1 典型放置示例

新增知识库详情查询、知识库删除约束：
- 放 `knowledge`

新增文档上传、文档版本切换、切片列表：
- 放 `document`

新增向量召回、上下文拼装、检索参数控制：
- 放 `retrieval`

新增模型探活、模型默认路由、提供方接入：
- 放 `model`

新增任务进度查询、任务 SSE 推送、任务重试记录：
- 放 `task`

新增 Milvus、MinIO、百炼、OCR 访问适配：
- 放 `infra`

新增后台聚合概览、统计报表、只读聚合查询：
- 放 `statistics`

### 6.2 判断顺序

新增代码时按下面顺序判断归属：

1. 先判断属于哪个业务域
2. 再判断属于域内哪一层
3. 只有在当前域出现明显专项职责时，才新增专项子包

不允许跳过第 1 步，直接按技术类型放到全局目录。

## 7. 当前阶段的落地要求

本项目当前最合适的演进策略不是“大规模目录重构”，而是：

- 保持顶层业务域结构稳定
- 后续新增代码严格遵循本规范
- 持续收敛 `common`
- 持续把外部依赖下沉到 `infra`
- 持续把复杂流程从普通 CRUD `service` 中抽离到更明确的位置

## 8. 一句话原则

一句话总结本项目后端目录规范：

顶层按业务域拆分，域内保持小分层，外部依赖统一下沉到 `infra`，不回退到全局技术层平铺结构。
