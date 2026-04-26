# ragAdmin 文档加载架构设计

## 1. 文档定位

本文档定义 ragAdmin 文档加载阶段的架构设计。

加载阶段是 RAG 文档入库流水线的第一环节，负责把不同格式的原始文件转换成统一的 `List<Document>`，供后续清洗、分块、向量化消费。

与加载阶段相关的其他文档：

- 清洗：`rag-admin-document-cleaning-architecture.md`
- 分块：`rag-admin-document-chunking-architecture.md`
- 语义分块：`rag-admin-semantic-chunking-architecture.md`
- 图片处理 + 内容感知分块：`document-loading-chunking-design.md`
- 入口编排：`rag-admin-document-ingestion-architecture.md`

## 2. 设计边界

加载阶段只做：

- 文件类型识别
- 解析能力路由
- 文本与基础 metadata 抽取

不做：

- 激进去噪（归属清洗阶段）
- 特殊符号删改（归属清洗阶段）
- 文本切片与 chunk 设计（归属分块阶段）
- 向量化（归属 Embedding 阶段）

一句话边界：**加载只负责"把文件变成 `List<Document>`"，不负责让它变干净或切成块。**

## 3. 总体结论

### 3.1 统一输出标准

- 标准结果：`org.springframework.ai.document.Document`
- 统一形态：`List<Document>`

所有格式最终收口为 `List<Document>`，后续清洗、切片、向量化统一消费这个结果。

### 3.2 读取职责边界

加载阶段的核心职责：

1. 根据文件类型和内容特征选择最合适的解析策略
2. 对外统一提供 `read()` 接口
3. 统一返回 `List<Document>`

### 3.3 核心原则：MinerU 统一驱动

上一版架构将 PDF 分为"文本型/扫描型/复杂版式"三类，对 PDF 做三级降级（Paragraph → Page → MinerU），对 DOCX/PPTX/XLSX 用 Tika 纯文本提取。这带来两个问题：

**问题 1：PDF 质量不一致。** Spring AI 的 ParagraphPdfDocumentReader 和 PagePdfDocumentReader 对表格、多栏布局、公式处理能力弱，且 ParagraphReader 在缺少 TOC 的 PDF 上会直接抛异常。而 MinerU 的 VLM 模型对所有 PDF 都能提供更好的版面理解、表格识别和 OCR。

**问题 2：Office 文档结构丢失。** Tika 的 `parseToString()` 把 DOCX/PPTX/XLSX 扁平化为纯文本，表格丢失行列结构、图片被丢弃、布局线性化，对 RAG 检索质量是负资产。

**结论**：以 MinerU 为核心解析引擎，所有包含复杂版面、表格、图片的文档（PDF、DOCX、PPTX、图片）统一走 MinerU 链路。纯文本格式（TXT、MD、HTML）保持轻量解析。XLSX 用专用表格提取。

## 4. 分层设计

### 4.1 文档识别层

负责识别三个关键信息：

| 信息 | 说明 | 用途 |
|------|------|------|
| `docType` | 文件类型，如 `PDF`、`DOCX`、`XLSX` | 策略路由首要依据 |
| `parseMode` | 解析模式：`TEXT` 或 `OCR` | metadata 标记 |
| `readerType` | 实际使用的 reader 标识 | metadata 标记，可追溯 |

识别依据：

- 文件扩展名（首要）
- 未来可扩展为 MIME 检测或文件头嗅探

### 4.2 读取策略层

采用策略模式 + 路由器模式：

```java
public interface DocumentReaderStrategy {
    boolean supports(DocumentParseRequest request);
    List<Document> read(DocumentParseRequest request) throws Exception;
}
```

```java
public interface DocumentReaderRouter {
    List<Document> read(DocumentParseRequest request) throws Exception;
}
```

路由器按 `@Order` 优先级遍历所有策略，第一个 `supports()` 返回 `true` 的策略负责解析。无匹配时抛出异常。

### 4.3 四层优先级

```
Tier 1: MinerU 驱动 — PDF / 图片 / Office 文档（通过 PDF 转换）
Tier 2: 轻量结构化解析 — MD / HTML / TXT
Tier 3: 专用表格提取 — XLSX
Tier 4: Tika 兜底 — 未知格式
```

## 5. 不同文档类型的读取策略

### 5.1 TXT

- **策略**：`TextDocumentReaderStrategy`（`@Order(10)`）
- **引擎**：Spring AI `TextReader`
- **输出**：原样文本包装为 `Document`
- **parseMode**：`TEXT`
- **readerType**：`TEXT_READER`

适用特点：纯文本，结构简单，无复杂版式。

### 5.2 Markdown

- **策略**：`MarkdownDocumentReaderStrategy`（`@Order(20)`）
- **引擎**：Spring AI `MarkdownDocumentReader`
- **输出**：按标题层级分节的 `Document` 列表
- **parseMode**：`TEXT`
- **readerType**：`MARKDOWN_READER`

适用特点：标题层级天然明确，适合知识库场景。Spring AI 的 MarkdownDocumentReader 已能按 heading 分节。

### 5.3 HTML

- **策略**：`HtmlDocumentReaderStrategy`（`@Order(30)`）
- **引擎**：Spring AI `JsoupDocumentReader`
- **输出**：去除标签后的文本 `Document`
- **parseMode**：`TEXT`
- **readerType**：`HTML_READER`

约束：HTML 的关键不是"能不能读"，而是"读哪一块"。不建议无选择器全页面直读。

### 5.4 PDF — 统一 MinerU

- **策略**：`MineruDocumentReaderStrategy`（`@Order(50)`）
- **引擎**：MinerU API（VLM 模型）
- **输出**：Markdown 格式的 `Document` 列表
- **parseMode**：`OCR`
- **readerType**：`MINERU_API`

**为什么统一走 MinerU：**

1. **版面理解能力**：MinerU 的 VLM 模型能识别多栏布局、阅读顺序、图文混排，Spring AI 的 Paragraph/Page Reader 不具备
2. **表格识别**：MinerU 能将 PDF 中的表格识别并输出为 Markdown 表格，Spring AI Reader 会把表格摊平为纯文本
3. **OCR 兜底**：即使是文本型 PDF 中夹杂的图片、公式、水印文字，MinerU 也能处理
4. **质量一致性**：不再存在"文本型 PDF 用 Reader、扫描型 PDF 用 MinerU"的质量断层
5. **复杂度降低**：移除 Paragraph → Page → MinerU 的三级降级逻辑，减少故障点

**MinerU API 调用流程：**

```
PDF 文件字节 → 上传至 MinIO → 生成预签名 URL → 提交 MinerU 任务 → 轮询等待 → 下载 Markdown 结果
```

**MinerU 不可用时的降级策略：**

- 如果 MinerU API 未启用或不可用，回退到 `TikaDocumentReaderStrategy`（`@Order(100)`）
- Tika 对 PDF 的提取质量有限，但至少能提供基础文本

### 5.5 DOCX / PPTX — 转 PDF 后 MinerU

- **策略**：`OfficeToMineruReaderStrategy`（`@Order(40)`，新增）
- **引擎**：LibreOffice headless 转 PDF + MinerU API
- **输出**：Markdown 格式的 `Document` 列表
- **parseMode**：`CONVERT_THEN_OCR`
- **readerType**：`OFFICE_TO_MINERU`

**为什么不用 Tika：**

Tika 的 `parseToString()` 对 DOCX/PPTX 只能提取纯文本，存在以下硬伤：

- 表格丢失行列结构，变成平铺文字
- 图片完全丢弃
- PPTX 的幻灯片层级丢失
- DOCX 中的页眉页脚、脚注、批注混入正文
- 复杂排版（分栏、图文混排）全部线性化

**转换流程：**

```
DOCX/PPTX 文件 → LibreOffice headless 转 PDF → PDF 字节 → 上传 MinIO → MinerU 解析 → Markdown
```

**LibreOffice headless 调用：**

```bash
libreoffice --headless --convert-to pdf --outdir /tmp /input/document.docx
```

**关键设计决策：**

- LibreOffice 以子进程方式调用，不引入 Java 进程内嵌方案
- 转换超时配置化（默认 60s）
- 转换产物（临时 PDF）在 MinerU 解析完成后清理
- LibreOffice 不可用时降级到 Tika

### 5.6 XLSX — POI 表格感知提取

- **策略**：`XlsxTableAwareReaderStrategy`（`@Order(35)`，新增）
- **引擎**：Apache POI
- **输出**：Markdown 表格格式的 `Document` 列表
- **parseMode**：`TEXT`
- **readerType**：`XLSX_TABLE_AWARE`

**为什么 XLSX 不走 MinerU：**

1. XLSX 本质是结构化表格数据，不存在"版面理解"的需求
2. POI 直接读取单元格数据是零信息损失的，比"转 PDF → 再 OCR 识别表格"更精准
3. 避免不必要的 PDF 转换和外部 API 调用

**提取策略：**

- 每个 Sheet 生成一个 `Document`
- 表格内容转为 Markdown table 格式（`| col1 | col2 | ... |`）
- 保留表头行
- 空行跳过，合并单元格展开
- 多 Sheet 之间用 `---` 分隔

### 5.7 PNG / JPG / JPEG / WEBP

- **策略**：`MineruDocumentReaderStrategy`（`@Order(50)`）
- **引擎**：MinerU API
- **输出**：OCR 识别后的 Markdown `Document`
- **parseMode**：`OCR`
- **readerType**：`MINERU_API`

图片文件不走普通文本 reader，统一通过 MinerU 产出文本与结构化结果。

### 5.8 未知格式 — Tika 兜底

- **策略**：`TikaDocumentReaderStrategy`（`@Order(100)`）
- **引擎**：Apache Tika `parseToString()`
- **输出**：纯文本 `Document`
- **parseMode**：`TEXT`
- **readerType**：`TIKA`

**Tika 的新定位：**

Tika 不再作为任何主流格式（PDF、DOCX、PPTX、XLSX）的首选解析器，仅作为未知格式的最后兜底。它的价值在于格式覆盖广，能在没有专用策略时至少提取出基础文本。

## 6. Reader 路由策略

路由器按 `@Order` 优先级遍历，第一个匹配的策略负责解析：

| @Order | 策略 | 支持格式 | 引擎 |
|--------|------|---------|------|
| 10 | `TextDocumentReaderStrategy` | TXT, TEX, TEXT | Spring AI TextReader |
| 20 | `MarkdownDocumentReaderStrategy` | MD, MARKDOWN | Spring AI MarkdownDocumentReader |
| 30 | `HtmlDocumentReaderStrategy` | HTML, HTM | Spring AI JsoupDocumentReader |
| 35 | `XlsxTableAwareReaderStrategy` | XLSX | Apache POI（新增） |
| 40 | `OfficeToMineruReaderStrategy` | DOCX, PPTX | LibreOffice → MinerU（新增） |
| 50 | `MineruDocumentReaderStrategy` | PDF, PNG, JPG, JPEG, WEBP | MinerU API |
| 100 | `TikaDocumentReaderStrategy` | 兜底 | Apache Tika |

## 7. 新增组件设计

### 7.1 DocumentConversionService — 文档格式转换

**职责**：将 Office 文档（DOCX/PPTX）转换为 PDF

**接口**：

```java
public interface DocumentConversionService {
    boolean supportsConversion(String docType);
    byte[] convertToPdf(byte[] sourceContent, String sourceFileName);
}
```

**实现**：`LibreOfficeDocumentConversionService`

- 调用 `libreoffice --headless --convert-to pdf`
- 输入源文件字节，输出 PDF 字节
- 配置项：`rag.document.conversion.libreoffice-path`、`rag.document.conversion.timeout-seconds`

**降级**：LibreOffice 不可用时返回 null，由调用方决定是否降级到 Tika

### 7.2 XlsxTableAwareReaderStrategy — XLSX 表格提取

**职责**：用 Apache POI 按行列结构提取 XLSX 内容，输出 Markdown 表格

**关键逻辑**：

- 遍历所有 Sheet，每个 Sheet 生成一个 `Document`
- 第一行作为表头，构建 Markdown table
- 空行跳过，合并单元格展开
- 包含 Sheet 名称作为 metadata

**依赖**：项目已有 `tika-parsers-standard-package`（间接引入 POI），无需新增依赖

### 7.3 OfficeToMineruReaderStrategy — Office 转 PDF 后 MinerU

**职责**：组合 `DocumentConversionService` + `MineruParseService`

**流程**：

```
1. supports(): 判断 docType 是否为 DOCX 或 PPTX
2. read():
   a. 调用 DocumentConversionService.convertToPdf() 转换为 PDF
   b. 构造新的 DocumentParseRequest（docType 改为 PDF）
   c. 调用 MineruParseService.parse() 解析
   d. 补充 metadata: readerType=OFFICE_TO_MINERU, originalDocType=DOCX/PPTX
3. 降级: 如果转换失败，回退到 TikaDocumentReaderStrategy
```

## 8. 与清洗的关系

加载阶段统一输出 `List<Document>` 后，进入清洗阶段，再由分块层消费。

加载阶段输出携带的标准 metadata：

| 字段 | 说明 |
|------|------|
| `sourceFileName` | 原始文件名 |
| `docType` | 文件类型 |
| `readerType` | 使用的解析器标识 |
| `parseMode` | `TEXT` / `OCR` / `CONVERT_THEN_OCR` |
| `storageBucket` | MinIO 存储桶 |
| `storageObjectKey` | MinIO 对象键 |

去噪、断行修复、符号保护、页眉页脚处理等规则，见 `rag-admin-document-cleaning-architecture.md`。

## 9. 与旧架构的变更对比

| 维度 | 旧架构 | 新架构 |
|------|--------|--------|
| PDF 策略 | 三级降级：Paragraph → Page → MinerU | 统一 MinerU |
| PDF 分类 | 文本型 / 扫描型 / 复杂版式三类 | 不再区分，统一处理 |
| DOCX/PPTX | Tika `parseToString()`（纯文本） | LibreOffice 转 PDF → MinerU（Markdown） |
| XLSX | Tika `parseToString()`（纯文本） | Apache POI 表格感知提取（Markdown table） |
| Tika 定位 | DOCX/PPTX/XLSX 首选解析器 | 未知格式兜底 |
| MinerU 定位 | 扫描型 PDF + 图片的专用引擎 | 所有 PDF + 图片 + Office 文档的核心引擎 |
| 策略数量 | 6 个 | 7 个（新增 3 个，删除 1 个，调整 2 个） |
| 新增依赖 | 无 | LibreOffice headless（运行时依赖） |

**删除的策略**：`PdfDocumentReaderStrategy`（三级降级逻辑整体移除）

**调整的策略**：
- `MineruDocumentReaderStrategy`：从仅处理图片扩展为处理所有 PDF + 图片
- `TikaDocumentReaderStrategy`：从 DOCX/PPTX/XLSX 首选降级为兜底

## 10. 最终原则

1. **MinerU 统一驱动**：所有包含复杂版面、表格、图片的文档（PDF、DOCX、PPTX、图片）统一走 MinerU，确保一致的解析质量
2. **轻量格式保持轻量**：TXT、MD、HTML 本身是结构化文本，不需要重型解析，保持当前策略
3. **表格数据专用处理**：XLSX 用 POI 直接提取，信息零损失，不走 PDF 转换
4. **Tika 兜底不首选**：Tika 保留为未知格式的最后防线，不再承担主流格式的解析职责
5. **加载不做清洗不做分块**：加载阶段只负责"把文件变成 `List<Document>`"，不负责让它变干净或切成块
