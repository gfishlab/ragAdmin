# 图片处理管线 + 清洗增强 实施计划

> 对应架构设计：`docs/architectures/document-loading-chunking-design.md`
> 范围：Phase A 图片处理管线 + Phase B 清洗增强

## 实施步骤

### Step 1: 值对象 — ImageProcessingReport + ImageResolutionResult

**新建** `document/parser/ImageProcessingReport.java`

```java
public record ImageProcessingReport(
    int totalReferences,    // 扫描到的图片引用总数
    int resolvedCount,      // 成功转存数
    int failedCount,        // 失败数
    List<String> warnings   // 失败警告信息
) {
    public static ImageProcessingReport empty() { ... }
}
```

**新建** `document/parser/ImageResolutionResult.java`

```java
public record ImageResolutionResult(
    String markdown,                // URL 重写后的 Markdown
    ImageProcessingReport report    // 处理报告
) {}
```

无依赖，纯数据 record。

### Step 2: ImageReferenceResolver — 通用图片引用解析

**新建** `document/parser/ImageReferenceResolver.java`，`@Component`

**职责**：扫描 Markdown 中的 `![alt](url)` 引用，按类型处理：

| URL 类型 | 处理方式 |
|----------|---------|
| HTTP/HTTPS（图床 CDN） | 下载 → 上传 MinIO → URL 重写 |
| 相对路径（images/fig.png） | 单文件上传无法解析，保留原样 + 警告 |
| 绝对路径本地（/data/fig.png） | 服务端无法访问，保留原样 + 警告 |
| MinIO 内部引用 | 跳过（已是持久链接） |

**依赖**：`MinioClientFactory`、`MinioProperties`、`IO_VIRTUAL_TASK_EXECUTOR`

**核心方法**：
```java
public ImageResolutionResult resolveImages(
    String markdown, String bucket, Long kbId, Long documentId
)
```

**并发模型**：
- 每张图片的下载+上传作为独立 `CompletableFuture` 跑在虚拟线程上
- `Semaphore(10)` 控制并发度
- `CompletableFuture.allOf()` 等全部完成
- 全部完成后统一做 URL 重写（依赖每张图片的 MinIO 路径）

**MinIO 存储**：
- 对象键：`kb/{kbId}/images/{documentId}/{hash(url)}.{ext}`
- CDN 图片用 URL 的 SHA-256 哈希作为文件名避免冲突
- 访问方式：预签名 GET URL（30 分钟）

**HTTP 下载**：自建 `RestClient`（无 baseUrl），设置连接/读取超时 30s，单图上限 10MB

### Step 3: MineruImageProcessor — MinerU ZIP 图片处理

**新建** `document/parser/MineruImageProcessor.java`，`@Component`

**职责**：从 MinerU 返回的 ZIP 包中提取 images/ 目录，批量上传 MinIO，重写 Markdown

**依赖**：`MinioClientFactory`、`MinioProperties`、`IO_VIRTUAL_TASK_EXECUTOR`

**核心方法**：
```java
public ImageResolutionResult processImagesFromZip(
    byte[] zipBytes, String markdown, String bucket, Long kbId, Long documentId
)
```

**处理流程**：
1. `ZipInputStream` 遍历 ZIP，收集 `images/` 目录下所有文件到 `Map<String, byte[]>`
2. 并行上传每张图片到 MinIO（虚拟线程 + Semaphore(10)）
3. 对象键：`kb/{kbId}/images/{documentId}/{原始文件名}`（保留 MinerU 命名如 `page_3_fig_1.png`）
4. 生成预签名 URL
5. 重写 Markdown 中 `![](images/xxx.png)` → `![](presignedUrl)`
6. 返回 `ImageResolutionResult`

### Step 4: MineruParseService — 新增图片感知解析方法

**修改** `document/parser/MineruParseService.java`（接口）

新增两个方法：
```java
List<Document> parseWithImages(DocumentParseRequest request) throws Exception;
List<Document> parseByUrlWithImages(String presignedUrl, String fileName,
    String bucket, Long kbId, Long documentId) throws Exception;
```

**修改** `document/parser/DefaultMineruParseService.java`

新增依赖：`ImageReferenceResolver`、`MineruImageProcessor`

新增核心方法 `resolveMarkdownWithImages(TaskResultData, bucket, kbId, documentId)`：

```
fullZipUrl 可用 → 下载 ZIP → MineruImageProcessor → 重写后 MD
fullMdUrl 可用  → 下载 MD  → ImageReferenceResolver（处理 CDN 图片）→ 重写后 MD
mdUrl 可用      → 同上
否则             → 抛 MINERU_RESULT_MISSING
```

`parseWithImages(request)` 和 `parseByUrlWithImages(...)` 都调用此方法。

**保持 `resolveMarkdown()` 不变**，向后兼容现有测试。

### Step 5: 四个 ReaderStrategy 集成图片处理

**修改** `document/parser/MineruDocumentReaderStrategy.java`

read() 中 `parse(request)` → `parseWithImages(request)`，一行改动。

**修改** `document/parser/OfficeToMineruReaderStrategy.java`

read() 中 `parseByUrl(presignedUrl, fileName)` → `parseByUrlWithImages(presignedUrl, fileName, bucket, kbId, documentId)`。

**修改** `document/parser/MarkdownDocumentReaderStrategy.java`

新增依赖：`ImageReferenceResolver`
read() 中 MarkdownDocumentReader 读取后，对每个 Document 的 text 调用 `imageReferenceResolver.resolveImages()`，用重写后的文本创建新 Document。

**修改** `document/parser/HtmlDocumentReaderStrategy.java`

同 MarkdownDocumentReaderStrategy 模式：JsoupDocumentReader 读取后调用 ImageReferenceResolver。

### Step 6: SafeNormalizationCleaner — 空行折叠优化

**修改** `document/parser/SafeNormalizationCleaner.java`

```java
// 改前：
.replaceAll("\n{3,}", "\n\n")
// 改后：
.replaceAll("\n{3,}", "\n")
```

3+ 连续换行折叠到 1 个换行（0 空行），RAG 文本更紧凑。

### Step 7: HtmlResidueCleaner — HTML 残留清理

**新建** `document/parser/HtmlResidueCleaner.java`，`@Component @Order(12)`

**职责**：清除文本中残留的 HTML 标签和实体

处理内容：
- HTML 实体转换：`&nbsp;` → 空格，`&amp;` → `&`，`&lt;` → `<`，`&gt;` → `>`，`&quot;` → `"`，`&#39;` → `'`
- HTML 标签去除：`<div>`、`<span>`、`<p>`、`<br>`、`<b>`、`<i>`、`<em>`、`<strong>` 等常见残留标签
- 多余空格折叠
- 不触碰 Markdown 格式符号（`#`、`|`、`-`、`*`）

supports()：始终返回 true（HTML 残留可能出现在任何文档类型中）

## 涉及文件清单

| # | 文件 | 操作 | 预估行数 |
|---|------|------|---------|
| 1 | `parser/ImageProcessingReport.java` | 新建 | ~25 |
| 2 | `parser/ImageResolutionResult.java` | 新建 | ~10 |
| 3 | `parser/ImageReferenceResolver.java` | 新建 | ~180 |
| 4 | `parser/MineruImageProcessor.java` | 新建 | ~130 |
| 5 | `parser/HtmlResidueCleaner.java` | 新建 | ~60 |
| 6 | `parser/MineruParseService.java` | 修改：+2 方法签名 | ~5 |
| 7 | `parser/DefaultMineruParseService.java` | 修改：+2 依赖，+3 方法 | ~80 |
| 8 | `parser/MineruDocumentReaderStrategy.java` | 修改：1 行 | ~1 |
| 9 | `parser/OfficeToMineruReaderStrategy.java` | 修改：~5 行 | ~5 |
| 10 | `parser/MarkdownDocumentReaderStrategy.java` | 修改：+1 依赖，~15 行 | ~20 |
| 11 | `parser/HtmlDocumentReaderStrategy.java` | 修改：+1 依赖，~15 行 | ~20 |
| 12 | `parser/SafeNormalizationCleaner.java` | 修改：1 字符 | ~1 |

所有文件在 `rag-admin-server/src/main/java/com/ragadmin/server/document/parser/` 下。

## 关键参考文件

- `infra/storage/support/MinioClientFactory.java` — MinIO 客户端创建
- `infra/storage/MinioProperties.java` — MinIO 配置（bucketName, publicUrl）
- `document/parser/MineruTaskModels.java` — TaskResultData record
- `document/parser/MineruProperties.java` — MinerU API 配置
- `document/entity/DocumentEntity.java` — kbId, id 字段
- `document/entity/DocumentVersionEntity.java` — storageBucket, storageObjectKey
- `common/config/AsyncExecutionConfiguration.java` — IO_VIRTUAL_TASK_EXECUTOR

## 验证

1. `mvn compile -pl rag-admin-server` 编译通过
2. 现有测试全部通过（`mvn test -pl rag-admin-server`）
3. 新增 ImageReferenceResolver 单元测试：CDN 图片转存、相对路径保留+警告、并行处理
4. 新增 MineruImageProcessor 单元测试：ZIP 提取、图片上传、URL 重写
5. 新增 HtmlResidueCleaner 单元测试：实体转换、标签去除、Markdown 保留
6. 回归：现有 MineruDocumentReaderStrategy/MarkdownDocumentReaderStrategy 测试更新 mock

## 风险点

| 风险 | 应对 |
|------|------|
| RestClient 下载超时/大文件 | 设置 30s 超时 + 10MB 大小限制 |
| Semaphore 未释放导致死锁 | try/finally 保证 release |
| 预签名 URL 30 分钟过期 | 管线内使用足够；后续展示用 publicUrl 或按需生成 |
| Spring AI Document 不可变 | new Document(rewrittenText, metadata) 创建新实例 |
| MinioClientFactory 每次创建新客户端 | 现有模式，MinioClient 轻量且线程安全，可接受 |
