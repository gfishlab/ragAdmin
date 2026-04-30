# 文档加载与分块管线完整设计

> 状态：方案设计中
> 范围：MinerU 云 API 规格、文档加载路径、图片处理生命周期、内容驱动分块策略选择

## 1. 问题背景

当前系统在文档加载和分块方面存在以下缺口：

1. **图片处理链路断裂**：MinerU VLM 提取 PDF/DOCX/PPT/图片时，返回的 ZIP 包含 .md 文件和 images/ 目录，但当前代码只取 .md，图片文件全部丢弃。Markdown 中的图片引用指向 MinerU 临时存储，链接会过期。
2. **非 VLM 文档的图片未处理**：MD、HTML 等直接解析的文档，其中的图片引用（相对路径、绝对路径、图床 CDN 链接）未做转存，在内网或外链失效时成为死链。
3. **分块策略未覆盖所有文档类型**：ContentAwareChunkStrategy 只对 MinerU 提取的内容生效，直接上传的 Markdown 文件即使包含表格/图片也不走内容感知分块。
4. **MinerU 云 API 规格未记录**：当前集成使用的 mineru.net 云 API 的请求/响应格式、返回内容结构没有在项目中形成文档。

## 1.1 部署环境约束

| 项目 | 值 |
|------|-----|
| ragAdmin 部署 | 腾讯云公有云服务器 |
| MinIO 部署 | 同一腾讯云环境 |
| MinerU 集成方式 | 云 API（mineru.net），暂不考虑自托管 |
| MinIO URL 可达性 | MinerU 云 API 可访问 MinIO 预签名 URL（公网可达） |

## 1.2 图片处理核心原则

**总体策略：图片保留，内容提取，链接不死。**

1. **图片转存是通用强制步骤**：无论文档类型（PDF/DOCX/PPT/MD/HTML/图片），提取出的或引用到的图片，一律转存到 MinIO，保证链接长期有效。配图是文档的有机组成部分，不应消灭图片。
2. **信息性图片的文字表达由 VLM 完成**：MinerU VLM 提取 PDF/DOCX/PPT 时，已对信息性图片（图表、数据可视化、流程图、截图中的文字）在 Markdown 中产出文字描述或表格表达。这些文字用于 RAG 检索匹配，图片文件用于前端展示。
3. **装饰性图片保持原引用**：风景、人物、装饰图标等对 RAG 检索无信息价值，不做额外文字描述。图片转存 MinIO 后保留原始引用，前端渲染时展示原图。
4. **图片描述增强不做强制生成**：不额外调用 VLM 对每张图片生成文字描述，避免增加噪声和成本。如未来需要，可作为独立可选步骤，默认关闭。
5. **所有图片源必须覆盖**：相对路径、绝对路径、图床 CDN、MinIO 内部引用，按优先级尝试解析和下载。
6. **不可解析的图片引用必须明确暴露**：服务端无法访问客户端本地绝对路径，不应静默忽略。对无法解析的图片引用给出警告提示，引导用户在上传前整理文档。

## 1.3 文档准入与预处理原则

企业级 RAG 落地的实践经验：**文档质量决定问答质量上限，技术管线只能在此基础上兜底**。

1. **上传前预校验**：MD/HTML 文件上传后、进入解析管线前，扫描所有图片引用，对不可解析的情况给出明确警告（非阻断）：
   - 绝对路径本地引用 → 警告："第 N 行图片引用为本地绝对路径，服务端无法访问，请改为相对路径或图床链接"
   - 相对路径但图片文件缺失 → 警告："图片文件未找到，请将 MD 连同图片目录打包为 ZIP 上传"
   - 图床 CDN 链接不可达 → 警告："图片链接无法访问，请确认链接有效"
2. **用户可选择忽略警告继续上传**：图片引用成为死链，但文字内容正常处理
3. **推荐用户在上传前人工整理文档**：修复不可访问的图片引用（本地绝对路径改为图床链接或打包上传）、扫描件图片优先走 OCR 提取文字、表格使用原生格式不用截图
4. **文档规范建议**：可在知识库管理界面提供文档准入检查清单，引导用户提交高质量语料
5. **图片不需要人工替换为文字**：配图是文档的有机组成部分，系统通过 VLM 提取信息性内容 + 图片转存保证不丢，人工只需确保图片引用可解析即可

## 2. MinerU 云 API 规格

### 2.1 当前集成方式

ragAdmin 使用 MinerU 云端 API（`mineru.net`），非自托管 MinerU。

| 项目 | 值 |
|------|-----|
| 基地址 | `https://mineru.net` |
| 认证 | Bearer Token |
| 模型版本 | `vlm`（视觉语言模型） |

### 2.2 提交任务

```
POST /api/v4/extract/task
Headers:
  Authorization: Bearer {api_token}
  Content-Type: application/json

Body:
{
  "url": "https://minio.xxx.com/bucket/xxx.pdf",  // 文件可访问的预签名 URL
  "model_version": "vlm",                           // VLM 视觉模型
  "file_name": "xxx.pdf"                            // 原始文件名
}
```

- `url`：必须是公网可访问的 URL（当前通过 MinIO 预签名 GET URL 实现，30 分钟有效）
- `model_version`：固定为 `vlm`
- 支持的文件类型：PDF、PNG、JPG、JPEG、WEBP、DOCX（经 LO 转 PDF 后传入）、PPTX（同上）

### 2.3 轮询结果

```
GET /api/v4/extract/task/{task_id}
Headers:
  Authorization: Bearer {api_token}

Response:
{
  "code": 0,
  "msg": "success",
  "data": {
    "task_id": "xxx",
    "state": "done",                  // pending → processing → done / failed
    "full_zip_url": "https://...",    // ★ 完整 ZIP 包（含 MD + 图片）
    "full_md_url": "https://...",     // 完整 Markdown 文件
    "md_url": "https://...",          // 简版 Markdown 文件
    "err_msg": null
  }
}
```

### 2.4 返回内容结构

**`full_zip_url` 指向的 ZIP 包结构**：

```
├── full.md                ← 完整 Markdown（含图片引用）
└── images/                ← 提取出的图片文件
    ├── page_3_fig_1.png
    ├── page_5_table_1.png
    └── ...
```

Markdown 中的图片引用格式：`![](images/page_3_fig_1.png)` — 指向 ZIP 内 images/ 的相对路径。

**`full_md_url` / `md_url`**：纯 Markdown 文本，不含图片文件。如果 MD 中有图片引用，URL 可能指向 MinerU 临时存储（会过期）。

### 2.5 当前代码处理逻辑

```
resolveMarkdown() 优先级：
  1. fullMdUrl → downloadPlainText() 直接下载文本
  2. mdUrl → downloadPlainText() 直接下载文本
  3. fullZipUrl → downloadMarkdownFromZip() 仅提取 .md，丢弃 images/

结果：Markdown 中的图片引用成为死链（指向 MinerU 临时 URL 或相对路径）
```

### 2.6 MinerU 部署模式说明

当前使用 **MinerU 云 API（mineru.net）**，暂不考虑自托管。

| 维度 | 云 API（当前） | 自托管（未来可选） |
|------|----------------|-------------------|
| 部署要求 | 无 | 需要 GPU 服务器 |
| 数据流向 | 文件经公网传至 MinerU | 数据不出内网 |
| 网络要求 | MinIO 预签名 URL 需公网可达 | 无需公网 |
| 成本 | 按量计费 | GPU 一次性投入 |
| 当前适配 | ✅ 腾讯云部署，MinIO URL 公网可达 | 内网部署时考虑 |

代码层面，`MineruSourceResolver` 当前生成 MinIO 预签名 URL，未来切自托管时只需改为 MinIO 内网地址直连。

## 3. 文档加载路径设计

### 3.1 加载策略矩阵

| 文档类型 | 加载工具 | 提取方式 | 输出格式 | 选择理由 |
|----------|----------|----------|----------|----------|
| PDF | MinerU VLM | 视觉理解 | Markdown + 图片 | 版面理解能力强，能处理扫描件 |
| DOCX | MinerU VLM | 视觉理解 | Markdown + 图片 | MinerU 原生支持 Office 格式 |
| PPT/PPTX | MinerU VLM | 视觉理解 | Markdown + 图片 | MinerU 原生支持 Office 格式 |
| PNG/JPG/JPEG/WEBP | MinerU VLM | 视觉理解 | Markdown（图片描述） | 直接视觉识别 |
| MD/MARKDOWN | Spring AI MarkdownReader | 结构化解析 | 原始 Markdown | 已是结构化文本，无需 VLM |
| HTML/HTM | Jsoup | DOM 解析 | 纯文本 + 表格标记 | 已是结构化标记 |
| TXT/TEX/TEXT | Spring AI TextReader | 纯文本 | 原始文本 | 纯文本无需处理 |
| XLSX | Apache POI | 表格感知 | Markdown Table | 表格结构明确，POI 精确 |
| 其他 | Tika 兜底 | 通用解析 | 纯文本 | 兜底策略 |

### 3.2 核心原则

- **视觉内容走 VLM**：PDF、DOCX、PPT、图片 — 包含视觉排版、嵌入图片、复杂表格
- **文本内容直接解析**：Markdown、HTML、TXT — 已是结构化文本，走 VLM 浪费算力且可能丢格式
- **表格优先精确解析**：XLSX 走 POI，表格结构精确度高于 VLM

### 3.3 为什么 Markdown 不走 MinerU VLM

1. **已是结构化文本**：标题（`#`）、列表（`-`）、代码块（` ``` `）、表格（`|...|`）— VLM 会把这些格式信息丢失
2. **图片引用不需要 VLM 提取**：`![](https://图床/xxx.png)` 已经是可访问的 URL，直接保留即可
3. **算力浪费**：VLM 按 token 计费，对纯文本 MD 做视觉识别是浪费

## 4. 图片处理生命周期

### 4.0 通用图片转存原则（所有文档类型适用）

图片转存是独立于 VLM 提取的通用步骤，适用于所有文档类型：

```
图片来源分类与处理策略：

① MinerU VLM 提取的图片（PDF/DOCX/PPT）
   来源：ZIP 包内 images/ 目录
   特点：文件已在本地（ZIP 内），直接上传 MinIO
   URL 重写：![](images/xxx.png) → ![](minio://kb/{kbId}/images/{docId}/xxx.png)

② MD/HTML 文档中的图片引用
   来源分类：
   a. 相对路径（./images/fig.png, images/fig.png）
      → 解析为相对于文档存储路径
      → 如果上传时是 ZIP/目录打包，从包内查找并上传
      → 如果是单个 MD 文件，相对路径无法解析，保留原样
   b. 绝对路径本地（/data/docs/images/fig.png）
      → 服务端无法访问用户本地文件系统
      → 保留原样，日志记录警告
   c. 图床/CDN URL（https://cdn.xxx.com/fig.png）
      → 下载 → 上传 MinIO → URL 重写
      → 下载失败时保留原 URL，日志记录警告
   d. MinIO 内部引用（同桶内其他路径）
      → 验证可访问性
      → 按需转存到规范路径

③ 图片描述策略
   - 不额外调用 VLM 生成图片描述
   - MinerU VLM 提取时已对信息性图片（图表、流程图）在 Markdown 中产出描述
   - 装饰性图片保持原始引用（![](xxx.png)），不做文字补充
   - 保留 URL 重写后的图片引用在 Markdown 中，前端渲染时可直接展示图片
```

### 4.1 VLM 提取路径（PDF/DOCX/PPT）

```
原始文件 (PDF/DOCX/PPT)
  │
  ▼
① MinioPresignedMineruSourceResolver
   生成原始文件预签名 GET URL（30分钟有效）
  │
  ▼
② MinerU VLM API 提交任务
   POST /api/v4/extract/task { url, model_version, file_name }
  │
  ▼
③ 轮询直到 state=done
   GET /api/v4/extract/task/{task_id}
   获取 full_zip_url
  │
  ▼
④ 下载 ZIP 包，同时提取 MD 和图片 [改造点]
   a. 提取 full.md
   b. 提取 images/ 目录下所有图片文件
   c. 逐张上传到 MinIO：kb/{kbId}/images/{documentId}/{imageName}
   d. 生成持久访问 URL
  │
  ▼
⑤ Markdown 图片 URL 重写 [改造点]
   扫描 full.md 中的图片引用：
   ![](images/page_3_fig_1.png)
   → ![]({minioBaseUrl}/kb/{kbId}/images/{documentId}/page_3_fig_1.png)
  │
  ▼
⑥ 使用 URL 重写后的 Markdown 进入后续管线
   → 清洗 → 信号分析 → 分块 → 入库
```

### 4.2 直接解析路径（MD/HTML）图片处理

```
MD/HTML 文件
  │
  ▼
① Spring AI MarkdownReader / Jsoup 直接解析
   获得原始文本（含图片引用）
  │
  ▼
② ImageReferenceResolver 扫描所有图片引用 [新建]
   a. 按引用类型分类（相对/绝对/CDN/MinIO内部）
   b. 尝试下载或解析每张图片
   c. 上传到 MinIO：kb/{kbId}/images/{documentId}/{imageName}
   d. URL 重写为 MinIO 持久链接
   e. 不可解析的引用保留原样，日志警告
  │
  ▼
③ 使用 URL 重写后的内容进入后续管线
   → 清洗 → 信号分析 → 分块 → 入库
```

### 4.3 图片存储规范

| 项目 | 规范 |
|------|------|
| 存储桶 | 与原始文件相同的 MinIO bucket |
| 对象键 | `kb/{kbId}/images/{documentId}/{imageName}` |
| 访问方式 | 预签名 GET URL（与原始文件一致） |
| 命名规则 | MinerU 提取：保留原始文件名（如 `page_3_fig_1.png`）；MD/HTML 引用：用 URL hash 去重命名 |
| 生命周期 | 与文档版本绑定，文档删除时级联清理 |

### 4.4 无图片的快速路径

- `full_md_url` 返回且 Markdown 中无图片引用 → 直接使用，无需图片处理
- `full_zip_url` 返回 → 始终走 ZIP 解压路径（包含图片处理步骤）
- MD/HTML 内容中无图片引用 → 跳过 ImageReferenceResolver

### 4.5 并发与性能优化（JDK 21 虚拟线程）

图片处理管线中大量操作是 IO 密集型（网络下载、MinIO 上传），应利用 JDK 21 虚拟线程并行化。

**当前虚拟线程使用情况**：
- `DocumentParseProcessor` 的任务分发已使用 `IO_VIRTUAL_TASK_EXECUTOR`（每个文档解析任务跑在独立虚拟线程上）

**图片处理需要的并发优化**：

```
单文档内的图片处理并发模型：

阶段 1：图片下载 + MinIO 上传（并行）
  ┌─ image_1: download() → minioUpload() ─┐
  ├─ image_2: download() → minioUpload() ─┤
  ├─ image_3: download() → minioUpload() ─┤→ CompletableFuture.allOf()
  ├─ ...                                   │
  └─ image_N: download() → minioUpload() ─┘
                                            ↓
阶段 2：URL 重写（串行，依赖阶段 1 结果）
  扫描 Markdown → 替换所有图片引用为 MinIO URL
```

**关键约束**：
- 阶段 2（URL 重写）必须等阶段 1 全部完成 — 每张图片的 MinIO 路径是重写的输入
- 单张图片的下载+上传是原子操作，不需要和其他图片协调
- 并发度上限控制：单文档内最多 `maxImageConcurrency`（默认 10）个图片同时处理，避免大量图片时 MinIO 压力过大

**实现方式**：
- 使用 `CompletableFuture` + `Executors.newVirtualThreadPerTaskExecutor()` 或直接 `Thread.ofVirtual().start()`
- 不引入额外线程池，虚拟线程由 JVM 调度，不需要池化
- 并发度通过 `Semaphore` 控制

**适用范围**：
- MinerU ZIP 内图片批量上传 MinIO
- MD/HTML 中 CDN 图片批量下载 + 上传
- 文档删除时图片批量清理

### 4.6 需要新增的代码

| 组件 | 说明 |
|------|------|
| `ImageReferenceResolver` | **通用组件**：扫描 Markdown 中的图片引用，按类型解析下载，上传 MinIO，重写 URL |
| `MineruImageProcessor` | MinerU 专用：ZIP 内图片提取 + 调用 ImageReferenceResolver 重写 |
| `DefaultMineruParseService.resolveMarkdownWithImages()` | 新方法：ZIP 解压 + 图片处理 + URL 重写 |
| `DefaultMineruParseService.downloadMarkdownFromZip()` | 改造：同时返回图片 Map |
| `MineruDocumentReaderStrategy.read()` | 调用新的图片处理流程 |
| `OfficeToMineruReaderStrategy.read()` | 同上 |
| `MarkdownDocumentReaderStrategy.read()` | 解析后调用 ImageReferenceResolver |
| `HtmlDocumentReaderStrategy.read()` | 同上 |

## 5. 清洗管线设计

### 5.0 完整管线顺序

```
加载（extract）→ 图片转存+URL重写（imageProcess）→ 清洗（clean）→ 信号分析（signals）→ 分块（chunk）
```

图片转存+URL重写属于加载阶段的一部分，清洗在其后执行，确保清洗操作在干净的 URL 结构上工作。

### 5.1 当前清洗策略矩阵

| 清洗步骤 | Order | 启用条件 | 处理内容 |
|---------|-------|---------|---------|
| SafeNormalizationCleaner | 10 | 始终启用 | 换行符归一化、空行折叠、尾部空格清理 |
| OcrNoiseCleaner | 15 | OCR 模式 + 检测到噪声 | Unicode 乱码、控制字符 |
| PdfHeaderFooterCleaner | 20 | PDF+TEXT 模式 + 检测到重复页眉页脚 | 每页重复的首行/末行文本 |
| LineMergeCleaner | 25 | PDF+TEXT 模式 + 段落结构断裂 | 连续非空行合并为段落 |
| SemanticPreservingCleaner | 30 | 上述任一启用时 | 预留扩展点（当前空实现） |

### 5.2 清洗策略是信号驱动的条件组合

不是所有文档跑全部清洗步骤。`DefaultCleanerPolicyResolver` 根据文档类型 + 信号检测结果动态决定启用哪些步骤：

- MD/HTML → 只走 SafeNormalization（结构化文本不需要复杂清洗）
- PDF+TEXT → 根据信号可能启用页眉页脚去除 + 行合并
- OCR 模式 → 根据信号可能启用噪声清理

### 5.3 MD/HTML 清洗增强（待实施）

当前 MD/HTML 只走换行归一化，实际可能还需要：

| 增强项 | 说明 | 优先级 |
|--------|------|--------|
| 空行折叠到 1 行 | 当前折叠到 2 行，RAG 不需要视觉间距，1 行更紧凑 | 高 |
| HtmlResidueCleaner | 清除 HTML 转 MD 残留的 `<div>`、`<span>`、`&nbsp;` 等标签和实体 | 中 |
| 无效图片引用警告 | 交给 ImageReferenceResolver 处理，不放进清洗步骤 | 已规划 |

### 5.4 TOC（目录）不需要清洗

所有文档类型的目录结构都不需要专门的 CleanerStep：

- **PDF/DOCX/PPT**：MinerU VLM 理解目录结构，章节标题转为 Markdown heading，目录信息已融入正文
- **MD**：`#`/`##` 标题本身就是目录结构，ContentAwareChunkStrategy 以 HEADING 块为分块边界，目录内容自然被正确处理
- **HTML**：`<h1>`~`<h6>` 同理

`tocOutlineMissing` 信号定位为**文档质量评估指标**（无目录结构可能说明文档不规范），不驱动清洗动作。

### 5.5 清洗粒度：文档级够用

当前文档级粒度（整篇文档统一策略）足够，不需要改成段落级。前提约束：

- **LineMergeCleaner 只在 PDF+TEXT 模式下启用** — 此模式下的 PDF 提取结果是纯文本，不存在 Markdown 管道表格被误合并的风险
- 如果未来扩展到其他模式，需要在合并逻辑里增加表格行检测（跳过 `|` 开头的行）

### 5.6 清洗可回溯性

清洗不可逆，但不需要存储完整的前后文本对比。记录以下信息即可：

- `DocumentCleanPolicy`：哪些清洗步骤被启用（记录到 TaskStepRecord 的 detailJson）
- 清洗统计：移除的页眉页脚行数、合并的行数、清除的噪声字符数
- 不存储清洗前后的完整文本（节省存储，实际也无必要）

## 6. 内容驱动分块策略选择

### 6.1 策略选择完全基于内容特征

分块策略选择不基于文档类型，只基于提取后的内容特征：

```
内容特征检测（DocumentSignals）
  │
  ├─ 检测到表格或图片 → ContentAwareChunkStrategy (Order=4)
  │    ├─ 表格：保持完整，超大按行拆分保留表头
  │    ├─ 图片引用：与上下文段落绑定
  │    └─ 混合内容：综合处理
  │
  ├─ 纯文本 + 有 embedding 模型 → SemanticChunkStrategy (Order=5)
  │    └─ 基于语义相似度的智能分块
  │
  └─ 兜底 → RecursiveFallbackStrategy (Order=50)
       └─ 按 maxChunkChars 固定切分（仅安全网）
```

### 6.2 各文档类型端到端流程

#### PDF（含嵌入图片、表格、图表）
```
PDF → MinerU VLM → ZIP(md + images/) → 图片存 MinIO → URL 重写
  → 清洗（页眉页脚） → 信号分析（表格/图片检测） → ContentAwareChunkStrategy
```

#### DOCX/PPT（含嵌入图片）
```
DOCX/PPTX → 原始文件存 MinIO → MinerU VLM（原生支持 Office 格式） → 同 PDF 路径
```

#### 图片（PNG/JPG）
```
图片 → MinerU VLM → Markdown（图片内容描述） → 清洗（OCR噪声） → 语义/内容感知分块
```

#### Markdown（含表格和图片引用）
```
MD → Spring AI MarkdownReader 直接读取 → ImageReferenceResolver（图片转存+URL重写）
  → 清洗（空行归一化、HTML残留清理）→ 信号分析（检测表格/图片）
  → 有表格/图片 → ContentAwareChunkStrategy
  → 纯文本 → SemanticChunkStrategy
```

#### HTML 网页
```
HTML → Jsoup → 纯文本 → 语义分块
```

#### TXT 纯文本
```
TXT → 直接读取 → SemanticChunkStrategy
```

#### XLSX 表格
```
XLSX → Apache POI → Markdown Table → ContentAwareChunkStrategy
```

### 6.3 分块配置参数（已实现：ChunkProperties）

| 内容类型 | 默认 maxChunkChars | 理由 |
|----------|-------------------|------|
| TEXT | 800 | 语义分块为主，800 为参考值 |
| TABLE | 1600 | 表格需要更大窗口保持完整 |
| IMAGE | 600 | 图片上下文紧凑 |
| MIXED | 1200 | 综合折中 |

## 7. 实施步骤

### Phase A：图片处理管线（最大缺口）

1. 新建 `MineruImageProcessor` — 图片下载 + MinIO 上传 + URL 重写
2. 改造 `DefaultMineruParseService` — ZIP 解压同时处理图片
3. 改造 `MineruDocumentReaderStrategy` / `OfficeToMineruReaderStrategy`
4. 图片存储路径规范与清理机制
5. 单元测试 + 集成测试

### Phase B：内容驱动分块（已完成主体）

- ✅ ChunkProperties 独立配置
- ✅ DocumentSignals 内容特征检测（表格/图片）
- ✅ ContentAwareChunkStrategy 内容感知分块
- ✅ SemanticChunkStrategy 内容过滤
- 待验证：用真实 MinerU 输出测试端到端效果

### Phase C：待确认事项

1. **MinerU 云 API 返回的 ZIP 是否总是包含 images/**：需要实际调用验证
2. **MinerU VLM 对图片（PNG/JPG）的处理方式**：是否返回图片描述还是原图
3. **图片上传到 MinIO 的性能**：大文档可能包含大量图片，需要批量上传优化
4. **MD 文件打包上传**：单个 MD 文件上传时，相对路径图片无法解析；是否要求用户打包成 ZIP 上传
5. **图片描述增强**：当前不做 VLM 图片描述；未来如需启用，可作为独立可选步骤
