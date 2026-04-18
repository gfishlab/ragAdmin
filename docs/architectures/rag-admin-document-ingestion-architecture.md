# ragAdmin 文档导入架构设计

## 1. 文档定位

本文档作为 `ragAdmin` 知识库文档导入专题的入口文档，只保留导入阶段的总原则与专题拆分关系。

当前阶段，文档导入拆分为两个稳定专题：

- 文档加载：见 `rag-admin-document-loading-architecture.md`
- 文档清洗：见 `rag-admin-document-cleaning-architecture.md`

如果局部实现与专题文档冲突，应优先更新对应专题文档，再调整实现。

## 2. 总体结论

`ragAdmin` 当前阶段将 `org.springframework.ai.document.Document` 作为文档导入阶段的统一结果，统一输出形态为 `List<Document>`。

导入主链路如下：

`原始文件 -> 文档识别与读取 -> List<Document> -> 清洗 -> 切片 -> 向量化 -> 入库`

其中：

- 文档加载阶段负责文件类型识别、读取器选择、`MinerU API` / 结构化解析路由
- 文档清洗阶段负责去噪、归一化、策略化增强清洗与 metadata 补齐
- 分块与向量化消费清洗后的 `Document` 列表，不直接依赖原始文件

## 3. 职责边界

- `DocumentReader`、`MinerU API` 等解析能力负责“读取”
- `DocumentCleaner` 与清洗策略负责“清洗”
- `chunk splitter` 负责“最终切片”

禁止混淆：

- `Tika` 不负责分块
- `MinerU API` / OCR 不负责向量化
- 清洗层不应承担最终 chunk 设计

## 4. 专题关系

### 4.1 文档加载专题

`rag-admin-document-loading-architecture.md` 负责定义：

- 文档识别层
- `DocumentReaderStrategy`
- `DocumentReaderRouter`
- 不同文件类型的 reader 选择
- 文本型 / 扫描型 / 复杂版式 `PDF` 的路由策略

### 4.2 文档清洗专题

`rag-admin-document-cleaning-architecture.md` 负责定义：

- 安全清洗与语义敏感清洗
- `DocumentCleaner`
- `CleanerPolicyResolver`
- 特殊符号保护原则
- 清洗与分块的边界
- `metadata` 补齐与清洗标记规范

## 5. 最终原则

一句话总结：

`ragAdmin` 的文档导入以 `List<Document>` 作为统一收口结果；文件类型选择与解析能力路由归属于“加载”，去噪、归一化、符号保护与策略控制归属于“清洗”。
