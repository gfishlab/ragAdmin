# ragAdmin 文档加载与清洗架构设计

## 1. 文档定位

本文档用于收敛 `ragAdmin` 在知识库文档导入阶段的正式技术设计，统一回答以下问题：

- 文档加载阶段的统一输出标准是什么
- 不同文件类型应如何选择 `DocumentReader`
- 哪些文档必须先走 `OCR` 或更强的结构化解析
- 文档清洗与分块的职责边界如何划分
- `metadata` 应如何约束，便于后续切片、检索与溯源

当前阶段如果局部实现与本文档冲突，应优先更新本文档，再调整实现。

## 2. 设计边界

本文档只覆盖知识库导入链路中的“文档加载与清洗”阶段，不覆盖以下内容：

- 系统总体架构，见 `rag-admin-architecture.md`
- API 契约，见 `rag-admin-api-design.md`
- 数据库结构，见 `rag-admin-schema-v1.sql`
- RAG 问答编排，见 `rag-admin-ai-orchestration-architecture.md`

## 3. 总体结论

### 3.1 当前统一输出标准

`ragAdmin` 当前阶段将 `org.springframework.ai.document.Document` 作为文档加载后的统一结果，统一输出形态为 `List<Document>`。

原因如下：

- Spring AI / Spring AI Alibaba 已围绕 `Document` 提供后续切片、向量化与检索能力
- 当前知识库导入主链路的核心需求是“稳定抽取文本并进入 RAG”，`content + metadata` 已能满足一期落地
- 额外引入项目内 `StructuredDocument` 作为强制中间模型，会增加抽象成本，但当前尚未形成足够收益

因此，一期默认链路为：

`原始文件 -> DocumentReader / OCR / 结构化解析 -> List<Document> -> 清洗 -> 切片 -> 向量化 -> 入库`

### 3.2 读取、清洗、分块的职责边界

- `DocumentReader` 或等价解析器：负责从原始文件中读取文本与基础元数据
- 清洗层：负责去噪、归一化、补齐统一 metadata、过滤低质量内容
- 分块层：负责将清洗后的 `Document` 切成适合 embedding 与检索的 chunk

禁止将这些职责混在一起：

- `Tika` 不负责分块
- `OCR` 不负责向量化
- `DocumentReader` 不应直接承担最终 chunk 设计

### 3.3 PDF 的特殊性

`PDF` 不能被视为单一类型，至少要区分三类：

- 文本型 `PDF`
- 扫描型 `PDF`
- 复杂版式 `PDF`

三者的加载策略不能完全相同。尤其是扫描型与复杂版式 `PDF`，不能只依赖通用 `PDF DocumentReader` 或 `Tika` 纯文本抽取。

## 4. 分层设计

### 4.1 文档识别层

职责：

- 基于扩展名、MIME、文件头、抽样内容识别文档类型
- 区分文本型 `PDF` 与扫描型 `PDF`
- 判断是否需要 `OCR` 或结构化增强解析

输入示例：

- `fileName`
- `extension`
- `mimeType`
- `Resource`
- `storageBucket`
- `storageObjectKey`

输出示例：

- `docType`
- `parseMode`
- `readerType`

推荐的 `parseMode`：

- `TEXT`
- `OCR`
- `LAYOUT`

### 4.2 读取策略层

职责：

- 根据文档类型选择最合适的 `DocumentReader` 或解析器
- 对外统一提供 `read()` 能力
- 统一返回 `List<Document>`

推荐接口：

```java
public interface DocumentReaderStrategy {

    boolean supports(DocumentParseRequest request);

    List<Document> read(DocumentParseRequest request) throws Exception;
}
```

推荐再提供一个路由器：

```java
public interface DocumentReaderRouter {

    DocumentReaderStrategy route(DocumentParseRequest request);
}
```

### 4.3 清洗标准化层

职责：

- 清理脏数据
- 统一换行与空白
- 删除重复页眉页脚
- 补齐统一 `metadata`
- 识别空文档、低质量文档、OCR 失败文档

当前阶段清洗层仍然以 `List<Document>` 为输入输出，不额外引入强制的中间模型。

### 4.4 分块层

职责：

- 按语义与长度限制生成稳定 chunk
- 优先保留段落、标题、页码等语义边界
- 为后续 `kb_chunk.metadata_json` 写入可溯源信息

重要原则：

- `reader` 负责尽量保留结构
- `chunk splitter` 负责最终切片
- 不应把“按页读取”简单等价为“最终按页入向量库”

## 5. 不同文档类型的读取策略

### 5.1 TXT

推荐实现：

- `TextReader`

适用特点：

- 纯文本
- 结构简单
- 无复杂版式

清洗重点：

- 统一换行符
- 删除多余空白行
- 清理不可见字符与乱码

### 5.2 Markdown

推荐实现：

- `MarkdownDocumentReader`

适用特点：

- 标题层级天然明确
- 适合知识库场景

清洗重点：

- 保留标题层级到 `metadata`
- 按配置决定是否保留代码块、引用块、表格文本
- 避免过早拍平成一整段文本

### 5.3 HTML

推荐实现：

- `JsoupDocumentReader`

适用特点：

- 网页、帮助中心、富文本页面

清洗重点：

- 通过 selector 只抽取正文区域
- 删除导航、页脚、广告、推荐阅读、版权区块
- 必要时保留链接文本与目标 URL

约束：

- 不建议无选择器全页面直读
- `HTML` 的关键不是“能不能读”，而是“读哪一块”

### 5.4 文本型 PDF

推荐实现：

- 优先 `ParagraphPdfDocumentReader`
- 兜底 `PagePdfDocumentReader`

使用原则：

- `ParagraphPdfDocumentReader` 优先用于质量较高、内部结构较完整的电子版 `PDF`
- `PagePdfDocumentReader` 用于段落抽取质量差时的回退，或需要稳定页码定位时

清洗重点：

- 删除页眉页脚
- 合并断行
- 保留 `pageNo`
- 标记来源 reader

结论：

- RAG 场景默认优先段落 reader
- 若段落抽取质量不稳定，则回退为按页读取后再统一切片

### 5.5 扫描型 PDF

推荐实现：

- `tess4j + tesseract`
- `PaddleOCR`
- `MinerU`

约束：

- 扫描型 `PDF` 本质上属于图像文本识别链路
- 不应默认使用普通 `PDF DocumentReader`
- 不应假定 `Tika` 可恢复高质量正文

推荐链路：

`扫描 PDF -> OCR / 结构化解析 -> List<Document> -> 清洗 -> 切片`

### 5.6 复杂版式 PDF

典型特征：

- 多栏排版
- 表格密集
- 公式较多
- 图文混排
- 图片中包含关键信息

推荐实现：

- 优先 `MinerU`
- 或采用等价的版面恢复与结构化解析能力

结论：

- 对复杂版式 `PDF`，`Tika` 与简单 `PDF DocumentReader` 只能作为降级兜底，不能作为默认最佳实现

### 5.7 DOCX / PPTX / XLSX

推荐实现：

- `TikaDocumentReader`

适用特点：

- 一期优先打通主链路
- 支持常见 Office 文档快速接入

局限：

- 表格、幻灯片层级、单元格关系的保真度有限

清洗重点：

- `DOCX`：标题、列表、段落去噪
- `PPTX`：页号、页内文本聚合
- `XLSX`：工作表名、行列信息补充到 `metadata`

### 5.8 PNG / JPG / JPEG / WEBP

推荐实现：

- `OCR` Reader

结论：

- 图片文件不走普通文本 reader
- 必须先通过 OCR 产出文本，再统一进入 `List<Document>` 链路

## 6. Reader 路由策略

当前阶段建议使用“文件类型 + 内容特征”双重路由，而不是只看后缀。

推荐路由如下：

- `TXT` -> `TextReaderStrategy`
- `MD` -> `MarkdownReaderStrategy`
- `HTML/HTM` -> `HtmlReaderStrategy`
- `PDF`
  - 文本型且结构较好 -> `PdfParagraphReaderStrategy`
  - 文本型但段落质量差 -> `PdfPageReaderStrategy`
  - 扫描型或复杂版式 -> `OcrOrLayoutPdfReaderStrategy`
- `DOCX/PPTX/XLSX` -> `TikaReaderStrategy`
- `PNG/JPG/JPEG/WEBP` -> `OcrImageReaderStrategy`

## 7. 清洗规则

### 7.1 通用清洗动作

所有 reader 的输出都应经过统一清洗：

- 统一 `\r\n` 为 `\n`
- 删除首尾空白
- 合并连续空白行
- 清理明显乱码字符
- 过滤无意义噪声文本
- 标记内容为空或内容过短的文档

### 7.2 文本质量判定

以下情况应判定为低质量输出，并进入失败、告警或回退逻辑：

- 文本为空
- 文本长度明显异常
- OCR 结果高比例乱码
- 重复页眉页脚占比过高
- 页面数很多但抽取结果极少

### 7.3 页眉页脚处理

对 `PDF` 场景应支持统一的页眉页脚清理能力：

- 允许按重复模式识别
- 允许按固定配置删除
- 删除动作必须在 `metadata` 中保留清洗标记

## 8. Metadata 约定

当前阶段 `Document.metadata` 应作为后续切片、溯源与审计的统一事实来源。

建议至少保留以下字段：

- `sourceFileName`
- `docType`
- `readerType`
- `parseMode`
- `pageNo`
- `sectionTitle`
- `storageBucket`
- `storageObjectKey`
- `cleaned`
- `cleanVersion`

按类型可补充字段：

- `sheetName`
- `slideNo`
- `htmlSelector`
- `ocrEngine`
- `layoutEngine`

后续入库到 `kb_chunk.metadata_json` 时，应优先继承这些字段，并追加 chunk 级信息，例如：

- `chunkSourceStart`
- `chunkSourceEnd`
- `chunkStrategy`

## 9. 与分块的关系

### 9.1 当前原则

文档读取结果统一为 `List<Document>` 后，不代表可以直接无清洗进入向量化。必须先经过清洗和必要的元数据补齐，再进入分块。

### 9.2 分块优先级

推荐优先保留以下边界：

- Markdown 标题边界
- PDF 段落边界
- 页码边界
- HTML 正文区块边界

长度控制应作为第二优先级，而不是第一优先级。

### 9.3 Reader 与 Chunk 的关系

- `PagePdfDocumentReader` 的“按页输出”只是读取结果
- 最终 chunk 是否按页落库，应由分块策略决定
- `ParagraphPdfDocumentReader` 也不是最终 chunk 方案，只是更好的语义起点

## 10. 当前阶段实施建议

### 10.1 一期推荐组合

- `TXT` -> `TextReader`
- `MD` -> `MarkdownDocumentReader`
- `HTML` -> `JsoupDocumentReader`
- `PDF` -> 优先 `ParagraphPdfDocumentReader`，失败回退 `PagePdfDocumentReader`
- `DOCX/PPTX/XLSX` -> `TikaDocumentReader`
- `PNG/JPG/JPEG/WEBP` -> `tess4j/tesseract OCR`

统一流转为：

`Reader -> Cleaner -> Splitter -> Embedding -> Vector Store`

### 10.2 二期增强方向

- 增加扫描 `PDF` 自动识别
- 引入 `MinerU` 处理复杂版式 `PDF`
- 为 `XLSX` / `PPTX` 引入更强结构保真策略
- 细化 `kb_chunk.metadata_json` 的字段规范

## 11. 最终原则

一句话总结：

`ragAdmin` 当前阶段以 `List<Document>` 作为文档加载后的统一标准结果；不同格式通过最合适的 reader 或 OCR 能力产出 `Document`，再由统一清洗层和分块层收口，而不是试图用一个通用 reader 覆盖所有复杂文档场景。
