# 查询预处理实施计划

## 目标

基于 `docs/architectures/rag-admin-query-rewriting-architecture.md` 第 2 节，实现查询预处理（PII 脱敏 + 内容过滤）。

## 管线位置

```
用户原始 query
  → 【QueryPreprocessService】← 新增
  → QueryRewritingService
  → 多路召回
  → 冲突检测
  → Reranking
  → 上下文组装
```

## 实施步骤

### Step 1：配置层

**新建** `QueryPreprocessProperties.java`（`retrieval/config/`）

```yaml
rag:
  retrieval:
    query-preprocess:
      enabled: true
      pii-mask:
        enabled: true
      content-filter:
        enabled: true
        block-enabled: false
        blocked-words:
          - # 可扩展
```

### Step 2：PII 脱敏规则

**新建** `PiiMaskRule.java`（`retrieval/model/`）

正则规则定义：

| 类型 | 正则 | 替换 |
|------|------|------|
| 身份证 | `\d{17}[\dXx]` | `[身份证号]` |
| 手机号 | `(?<!\d)1[3-9]\d{9}(?!\d)` | `[手机号]` |
| 邮箱 | `[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}` | `[邮箱]` |
| 银行卡 | `(?<!\d)\d{16,19}(?!\d)` | `[银行卡号]` |
| IPv4 | `(?<!\d)\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?!\d)` | `[IP地址]` |

用 `(?<!\d)` 和 `(?!\d)` 前后断言避免误匹配（如长数字串中的子串）。

### Step 3：内容过滤规则

**新建** `ContentFilterService.java`（`retrieval/service/`）

- 加载敏感词列表（内置默认 + 配置扩展）
- 匹配后替换为 `***`
- `block-enabled=true` 时直接拦截，返回提示信息
- 记录过滤日志（脱敏后的 query + 触发规则）

### Step 4：核心服务

**新建** `QueryPreprocessService.java`（`retrieval/service/`）

```java
public PreprocessResult preprocess(String query) {
    // 1. PII 脱敏
    String masked = piiMask(query);
    // 2. 内容过滤
    String filtered = contentFilter(masked);
    // 3. 返回结果（包含是否被修改的标记）
    return new PreprocessResult(filtered, modified, blocked);
}
```

注入位置：`RetrievalService.retrieve()` 和 `retrieveAcrossKnowledgeBases()` 的入口处，在 query 进入任何后续处理之前。

### Step 5：单元测试

- PII 脱敏：身份证、手机号、邮箱、银行卡号、IP 地址的正则匹配
- 内容过滤：敏感词替换、block 模式拦截
- 组合场景：同时包含 PII 和敏感词的 query
- 边界场景：空 query、纯数字 query、无匹配的 query
- 容错：预处理失败时返回原始 query

## 文件改动清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `retrieval/config/QueryPreprocessProperties.java` | 预处理配置 |
| 新建 | `retrieval/model/PiiMaskRule.java` | PII 脱敏规则定义 |
| 新建 | `retrieval/model/PreprocessResult.java` | 预处理结果 |
| 新建 | `retrieval/service/QueryPreprocessService.java` | 预处理入口服务 |
| 新建 | `retrieval/service/ContentFilterService.java` | 内容过滤服务 |
| 修改 | `retrieval/service/RetrievalService.java` | 注入预处理到 retrieve() 入口 |
| 修改 | `resources/application.yml` | 新增 query-preprocess 配置段 |
| 新建 | `retrieval/service/QueryPreprocessServiceTest.java` | 单元测试 |

## 依赖关系

```
Step 1 (配置) → Step 2 (PII 规则) → Step 4 (核心服务)
                                  ↗
             Step 3 (内容过滤) ──→
Step 4 → Step 5 (测试)
```
