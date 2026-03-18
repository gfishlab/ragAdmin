<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listVectorIndexes } from '@/api/statistics'
import { resolveErrorMessage } from '@/api/http'
import type { VectorIndexOverview } from '@/types/statistics'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const rows = ref<VectorIndexOverview[]>([])

const query = reactive({
  keyword: '',
  status: '',
  milvusStatus: '',
})

const kbStatusOptions = [
  { label: '全部知识库状态', value: '' },
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
]

const milvusStatusOptions = [
  { label: '全部 Milvus 状态', value: '' },
  { label: '已加载', value: 'UP' },
  { label: '未加载', value: 'NOT_LOADED' },
  { label: '未建索引', value: 'EMPTY' },
  { label: '异常', value: 'DOWN' },
  { label: '未知', value: 'UNKNOWN' },
]

const filteredRows = computed(() => {
  if (!query.milvusStatus) {
    return rows.value
  }
  return rows.value.filter((item) => item.milvusStatus === query.milvusStatus)
})

const summary = computed(() => {
  const list = filteredRows.value
  const totalKnowledgeBaseCount = list.length
  const indexedKnowledgeBaseCount = list.filter((item) => item.vectorRefCount > 0).length
  const totalVectorCount = list.reduce((sum, item) => sum + item.vectorRefCount, 0)
  const issueKnowledgeBaseCount = list.filter((item) => hasIssue(item)).length
  return {
    totalKnowledgeBaseCount,
    indexedKnowledgeBaseCount,
    totalVectorCount,
    issueKnowledgeBaseCount,
  }
})

function hasIssue(item: VectorIndexOverview): boolean {
  if (item.milvusStatus === 'DOWN' || item.milvusStatus === 'NOT_LOADED') {
    return true
  }
  return item.chunkCount > 0 && item.vectorRefCount < item.chunkCount
}

function kbStatusTagType(status: string): 'success' | 'info' {
  return status === 'ENABLED' ? 'success' : 'info'
}

function kbStatusLabel(status: string): string {
  return status === 'ENABLED' ? '启用' : '禁用'
}

function milvusStatusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'UP') {
    return 'success'
  }
  if (status === 'NOT_LOADED') {
    return 'warning'
  }
  if (status === 'DOWN') {
    return 'danger'
  }
  return 'info'
}

function milvusStatusLabel(status: string): string {
  if (status === 'UP') {
    return '已加载'
  }
  if (status === 'NOT_LOADED') {
    return '未加载'
  }
  if (status === 'EMPTY') {
    return '未建索引'
  }
  if (status === 'DOWN') {
    return '异常'
  }
  return '未知'
}

function modelSourceLabel(source?: string | null): string {
  if (source === 'DEFAULT') {
    return '平台默认'
  }
  return '知识库绑定'
}

function coveragePercent(item: VectorIndexOverview): number {
  if (item.chunkCount <= 0) {
    return 0
  }
  return Math.min(100, Math.round((item.vectorRefCount / item.chunkCount) * 100))
}

function coverageStatus(item: VectorIndexOverview): '' | 'success' | 'warning' | 'exception' {
  if (item.chunkCount <= 0) {
    return ''
  }
  if (item.vectorRefCount === 0) {
    return 'exception'
  }
  if (item.vectorRefCount >= item.chunkCount) {
    return 'success'
  }
  return 'warning'
}

function formatTime(value?: string | null): string {
  if (!value) {
    return '暂无'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    hour12: false,
  })
}

async function loadData(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    rows.value = await listVectorIndexes({
      keyword: query.keyword.trim() || undefined,
      status: query.status || undefined,
    })
  } catch (error) {
    rows.value = []
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleSearch(): Promise<void> {
  await loadData()
}

async function handleRefresh(): Promise<void> {
  await loadData()
}

async function handleReset(): Promise<void> {
  query.keyword = ''
  query.status = ''
  query.milvusStatus = ''
  await loadData()
}

async function openKnowledgeBase(kbId: number): Promise<void> {
  await router.push(`/knowledge-bases/${kbId}`)
}

onMounted(async () => {
  await loadData()
})
</script>

<template>
  <section class="vector-page">
    <header class="vector-head">
      <div>
        <h1 class="page-title">向量索引</h1>
        <p class="page-subtitle">
          这里展示当前知识库正在使用的向量模型、分块与向量引用数量，以及 Milvus 集合是否已可用于检索。
        </p>
      </div>
      <el-button @click="handleRefresh">刷新概览</el-button>
    </header>

    <section class="summary-grid">
      <article class="summary-card soft-panel">
        <span>知识库总数</span>
        <strong>{{ summary.totalKnowledgeBaseCount }}</strong>
        <p>当前筛选结果中的知识库数量</p>
      </article>
      <article class="summary-card soft-panel">
        <span>已建索引</span>
        <strong>{{ summary.indexedKnowledgeBaseCount }}</strong>
        <p>至少已有一条向量引用的知识库</p>
      </article>
      <article class="summary-card soft-panel">
        <span>向量总数</span>
        <strong>{{ summary.totalVectorCount }}</strong>
        <p>已写入并建立业务引用的向量条数</p>
      </article>
      <article class="summary-card soft-panel is-warning">
        <span>待处理问题</span>
        <strong>{{ summary.issueKnowledgeBaseCount }}</strong>
        <p>包含未加载、异常或覆盖不完整的知识库</p>
      </article>
    </section>

    <section class="filter-panel soft-panel">
      <div class="filter-grid">
        <el-input v-model="query.keyword" placeholder="知识库编码或名称" clearable />

        <el-select v-model="query.status" placeholder="知识库状态">
          <el-option
            v-for="item in kbStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-select v-model="query.milvusStatus" placeholder="Milvus 状态">
          <el-option
            v-for="item in milvusStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>

      <div class="filter-actions">
        <el-button @click="handleReset">重置</el-button>
        <el-button type="primary" @click="handleSearch">查询概览</el-button>
      </div>
    </section>

    <section class="table-panel soft-panel">
      <section v-if="loadError" class="table-error">
        <el-empty description="向量索引概览加载失败">
          <template #description>
            <p class="error-text">{{ loadError }}</p>
          </template>
          <el-button type="primary" @click="handleRefresh">重新加载</el-button>
        </el-empty>
      </section>

      <template v-else>
        <el-table :data="filteredRows" v-loading="loading" empty-text="当前没有向量索引数据" stripe>
          <el-table-column label="知识库" min-width="220">
            <template #default="{ row }">
              <div class="kb-cell">
                <strong>{{ row.kbName }}</strong>
                <small>{{ row.kbCode }}</small>
                <el-tag size="small" :type="kbStatusTagType(row.kbStatus)">
                  {{ kbStatusLabel(row.kbStatus) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="向量模型" min-width="220">
            <template #default="{ row }">
              <div class="model-cell">
                <strong>{{ row.embeddingModelName || '未解析' }}</strong>
                <small>{{ row.embeddingModelCode || '暂无模型编码' }}</small>
                <el-tag size="small" effect="plain">
                  {{ modelSourceLabel(row.embeddingModelSource) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="集合名称" min-width="220">
            <template #default="{ row }">
              <code v-if="row.collectionName" class="mono-text">{{ row.collectionName }}</code>
              <span v-else class="muted-text">未建索引</span>
            </template>
          </el-table-column>

          <el-table-column label="文档 / 成功文档" width="140">
            <template #default="{ row }">
              {{ row.documentCount }} / {{ row.successDocumentCount }}
            </template>
          </el-table-column>

          <el-table-column label="分块数" width="100">
            <template #default="{ row }">
              {{ row.chunkCount }}
            </template>
          </el-table-column>

          <el-table-column label="向量数" width="100">
            <template #default="{ row }">
              {{ row.vectorRefCount }}
            </template>
          </el-table-column>

          <el-table-column label="覆盖率" min-width="220">
            <template #default="{ row }">
              <div v-if="row.chunkCount > 0" class="coverage-cell">
                <el-progress
                  :percentage="coveragePercent(row)"
                  :status="coverageStatus(row)"
                  :stroke-width="8"
                />
                <small>{{ row.vectorRefCount }} / {{ row.chunkCount }}</small>
              </div>
              <span v-else class="muted-text">暂无切片</span>
            </template>
          </el-table-column>

          <el-table-column label="维度" width="90">
            <template #default="{ row }">
              {{ row.embeddingDim ?? '-' }}
            </template>
          </el-table-column>

          <el-table-column label="Milvus 状态" min-width="220">
            <template #default="{ row }">
              <div class="milvus-cell">
                <el-tag :type="milvusStatusTagType(row.milvusStatus)">
                  {{ milvusStatusLabel(row.milvusStatus) }}
                </el-tag>
                <small>{{ row.milvusMessage }}</small>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="最近写入" min-width="170">
            <template #default="{ row }">
              {{ formatTime(row.latestVectorizedAt) }}
            </template>
          </el-table-column>

          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openKnowledgeBase(row.kbId)">
                查看知识库
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </section>
  </section>
</template>

<style scoped>
.vector-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.vector-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.summary-card,
.filter-panel,
.table-panel {
  padding: 20px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-card span {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.summary-card strong {
  font-family: "Noto Serif SC", serif;
  font-size: 34px;
  line-height: 1;
}

.summary-card p {
  margin: 0;
  color: #6d5948;
  line-height: 1.6;
}

.summary-card.is-warning {
  background: linear-gradient(180deg, rgba(255, 244, 230, 0.92), rgba(255, 250, 243, 0.88));
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 18px;
}

.table-error {
  padding: 8px 0;
}

.error-text {
  margin: 0;
  color: #6d5948;
}

.kb-cell,
.model-cell,
.milvus-cell,
.coverage-cell {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kb-cell small,
.model-cell small,
.milvus-cell small,
.coverage-cell small {
  color: #7a6451;
  line-height: 1.5;
}

.mono-text {
  color: #8d4510;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  word-break: break-all;
}

.muted-text {
  color: #8a715e;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .vector-head {
    flex-direction: column;
  }

  .filter-grid,
  .summary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .summary-card,
  .filter-panel,
  .table-panel {
    padding: 18px;
  }

  .filter-actions {
    flex-direction: column;
  }
}
</style>
