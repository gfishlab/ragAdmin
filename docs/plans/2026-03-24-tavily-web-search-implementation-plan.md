# Tavily 联网搜索接入实现计划

> **给执行型 Agent：** 当前环境不启用子代理流程，直接按本计划在当前会话内执行。步骤使用 `- [ ]` 复选框语法跟踪。

**目标：** 为前台问答接入 Tavily 真实联网搜索，并补齐日志、失败降级、结果裁剪、健康检查和配置样例。

**架构：** 保持 `AppChatService -> WebSearchProvider` 现有调用边界不变，在 `infra.search` 内新增 Tavily 实现与配置装配。联网异常统一降级为空结果，系统健康检查补充 Tavily 状态，测试覆盖 Provider 行为与调用方可见状态。

**技术栈：** JDK 21、Spring Boot 3、WebClient、JUnit 5、Mockito

---

### Task 1: 补齐设计与配置骨架

**文件：**
- Create: `docs/plans/2026-03-24-tavily-web-search-design.md`
- Create: `docs/plans/2026-03-24-tavily-web-search-implementation-plan.md`
- Modify: `rag-admin-server/src/main/resources/application.yml`
- Modify: `rag-admin-server/src/main/resources/application-local-secret.example.yml`

- [ ] **Step 1: 写入设计文档**
- [ ] **Step 2: 写入实现计划**
- [ ] **Step 3: 在共享配置中增加 `rag.search` 公共参数**
- [ ] **Step 4: 在本地 secret 示例文件中增加 Tavily 配置样例**

### Task 2: 实现 Tavily Provider 与装配

**文件：**
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/WebSearchProperties.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/TavilyProperties.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/TavilyWebSearchProvider.java`
- Create: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/WebSearchConfiguration.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/infra/search/NoopWebSearchProvider.java`

- [ ] **Step 1: 先写 Provider 测试用例骨架，覆盖可用性、响应映射与裁剪**
- [ ] **Step 2: 实现类型安全配置类**
- [ ] **Step 3: 实现 Tavily HTTP 调用、异常处理和结果标准化**
- [ ] **Step 4: 实现自动装配逻辑，未配置时回退 `NoopWebSearchProvider`**
- [ ] **Step 5: 补充 `NoopWebSearchProvider` 日志，统一留痕格式**

### Task 3: 补齐调用链日志与降级表现

**文件：**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppChatService.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/app/service/AppPortalService.java`

- [ ] **Step 1: 为 `AppChatService` 增加联网调用的结构化日志**
- [ ] **Step 2: 保持运行期异常降级为空结果，并记录降级原因**
- [ ] **Step 3: 确认 `AppPortalService` 继续通过 `isAvailable()` 暴露前台可用状态**

### Task 4: 增加系统健康检查可观测性

**文件：**
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/system/dto/HealthCheckResponse.java`
- Modify: `rag-admin-server/src/main/java/com/ragadmin/server/system/service/SystemHealthService.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/web/AdminApiWebMvcTest.java`

- [ ] **Step 1: 为健康检查 DTO 增加 Tavily 状态字段**
- [ ] **Step 2: 在 `SystemHealthService` 中实现 Tavily 健康检查**
- [ ] **Step 3: 调整管理端健康检查接口测试断言**

### Task 5: 补充单元测试

**文件：**
- Create: `rag-admin-server/src/test/java/com/ragadmin/server/infra/search/TavilyWebSearchProviderTest.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/app/service/AppChatServiceTest.java`
- Modify: `rag-admin-server/src/test/java/com/ragadmin/server/app/service/AppPortalServiceTest.java`

- [ ] **Step 1: 测试 Tavily 可用性判断**
- [ ] **Step 2: 测试 Tavily 正常返回与裁剪**
- [ ] **Step 3: 测试 `AppChatService` 在联网异常下仍可继续问答**
- [ ] **Step 4: 测试 `AppPortalService` 对真实 Provider 可见状态的暴露**

### Task 6: 验证与收口

**文件：**
- Modify: `rag-admin-server/src/main/resources/application-local-secret.example.yml`

- [ ] **Step 1: 运行 Tavily Provider 相关测试**
  Run: `mvn -pl rag-admin-server -Dtest=TavilyWebSearchProviderTest,AppChatServiceTest,AppPortalServiceTest,AdminApiWebMvcTest test`
  Expected: PASS
- [ ] **Step 2: 如测试失败，按最小改动修正实现**
- [ ] **Step 3: 检查配置样例与日志输出是否符合安全要求**
- [ ] **Step 4: 整理最终改动说明与剩余风险**
