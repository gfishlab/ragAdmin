<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElEmpty, ElSkeleton } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getDocumentDetail, listDocumentVersions } from '@/api/knowledge-base'
import { resolveErrorMessage } from '@/api/http'
import type { DocumentDetail, DocumentVersion } from '@/types/knowledge-base'

const route = useRoute()
const router = useRouter()

const detailLoading = ref(true)
const detailError = ref('')
const detail = ref<DocumentDetail | null>(null)

const versionLoading = ref(false)
const versionError = ref('')
const versions = ref<DocumentVersion[]>([])
const versionPagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})

const documentId = computed(() => Number(route.params.id))
const hasVersions = computed(() => versions.value.length > 0)

function parseStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'PROCESSING' || status === 'PENDING') {
    return 'warning'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

function enabledLabel(enabled: boolean): string {
  return enabled ? '启用' : '停用'
}

function formatTime(value: string | null | undefined): string {
  if (!value) {
    return '暂无时间'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

async function loadDetail(): Promise<void> {
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await getDocumentDetail(documentId.value)
  } catch (error) {
    detail.value = null
    detailError.value = resolveErrorMessage(error)
  } finally {
    detailLoading.value = false
  }
}

async function loadVersions(): Promise<void> {
  versionLoading.value = true
  versionError.value = ''
  try {
    const response = await listDocumentVersions(documentId.value, {
      pageNo: versionPagination.pageNo,
      pageSize: versionPagination.pageSize,
    })
    versions.value = response.list
    versionPagination.total = response.total
  } catch (error) {
    versions.value = []
    versionPagination.total = 0
    versionError.value = resolveErrorMessage(error)
  } finally {
    versionLoading.value = false
  }
}

async function initialize(): Promise<void> {
  await loadDetail()
  if (!detailError.value) {
    await loadVersions()
  }
}

async function handleBack(): Promise<void> {
  if (detail.value?.kbId) {
    await router.push(`/knowledge-bases/${detail.value.kbId}`)
    return
  }
  await router.push('/tasks')
}

async function handleRetryDetail(): Promise<void> {
  await initialize()
}

async function handleRetryVersions(): Promise<void> {
  await loadVersions()
}

async function handleCurrentChange(pageNo: number): Promise<void> {
  versionPagination.pageNo = pageNo
  await loadVersions()
}

async function handleSizeChange(pageSize: number): Promise<void> {
  versionPagination.pageSize = pageSize
  versionPagination.pageNo = 1
  await loadVersions()
}

onMounted(async () => {
  await initialize()
})
</script>

<template>
  <section class="document-detail-page">
    <el-skeleton v-if="detailLoading" :rows="12" animated class="detail-loading soft-panel" />

    <section v-else-if="detailError" class="detail-error soft-panel">
      <el-empty description="文档详情加载失败">
        <template #description>
          <p class="error-text">{{ detailError }}</p>
        </template>
        <div class="error-actions">
          <el-button @click="handleRetryDetail">重新加载</el-button>
          <el-button type="primary" @click="handleBack">返回上级页面</el-button>
        </div>
      </el-empty>
    </section>

    <template v-else-if="detail">
      <header class="detail-head">
        <div>
          <p class="detail-eyebrow">Document / Detail</p>
          <h1 class="page-title">{{ detail.docName }}</h1>
          <p class="page-subtitle">
            当前页面聚焦文档主体信息与版本列表，切片浏览与版本操作后续补充。
          </p>
        </div>
        <div class="head-actions">
          <el-button @click="loadDetail">刷新详情</el-button>
          <el-button type="primary" @click="handleBack">返回上级页面</el-button>
        </div>
      </header>

      <section class="overview-grid">
        <article class="overview-card soft-panel">
          <span>文档 ID</span>
          <strong>{{ detail.documentId }}</strong>
          <p>当前文档的唯一业务标识。</p>
        </article>
        <article class="overview-card soft-panel">
          <span>解析状态</span>
          <strong>
            <el-tag :type="parseStatusType(detail.parseStatus)">{{ detail.parseStatus }}</el-tag>
          </strong>
          <p>解析链路当前状态以后台任务执行结果为准。</p>
        </article>
        <article class="overview-card soft-panel">
          <span>所属知识库</span>
          <strong>{{ detail.kbName || detail.kbId || '暂无' }}</strong>
          <p>文档当前所属的知识库上下文信息。</p>
        </article>
      </section>

      <section class="detail-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>文档信息</h2>
            <p>首版文档详情页先完整展示基础字段与存储信息。</p>
          </div>
        </div>

        <div class="detail-matrix">
          <article class="detail-item">
            <span>文档类型</span>
            <strong>{{ detail.docType }}</strong>
          </article>
          <article class="detail-item">
            <span>启停状态</span>
            <strong>{{ enabledLabel(detail.enabled) }}</strong>
          </article>
          <article class="detail-item">
            <span>创建时间</span>
            <strong>{{ formatTime(detail.createdAt) }}</strong>
          </article>
          <article class="detail-item">
            <span>更新时间</span>
            <strong>{{ formatTime(detail.updatedAt) }}</strong>
          </article>
        </div>

        <div class="storage-panel">
          <article class="storage-item">
            <span>存储桶</span>
            <strong>{{ detail.storageBucket || '暂无' }}</strong>
          </article>
          <article class="storage-item">
            <span>对象 Key</span>
            <strong>{{ detail.storageObjectKey || '暂无' }}</strong>
          </article>
        </div>
      </section>

      <section class="version-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>版本列表</h2>
            <p>当前仅提供只读浏览，不包含激活版本与新增版本操作。</p>
          </div>
          <el-button :loading="versionLoading" @click="handleRetryVersions">刷新版本列表</el-button>
        </div>

        <section v-if="versionError" class="version-error">
          <el-empty description="版本列表加载失败">
            <template #description>
              <p class="error-text">{{ versionError }}</p>
            </template>
            <el-button type="primary" @click="handleRetryVersions">重新加载</el-button>
          </el-empty>
        </section>

        <template v-else>
          <el-table :data="versions" v-loading="versionLoading" empty-text="当前文档暂无版本数据" stripe>
            <el-table-column prop="versionId" label="版本 ID" width="100" />
            <el-table-column prop="storageObjectKey" label="对象 Key" min-width="280" />
            <el-table-column label="内容哈希" min-width="220">
              <template #default="{ row }">
                {{ row.contentHash || '暂无' }}
              </template>
            </el-table-column>
            <el-table-column label="激活状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '当前生效' : '历史版本' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="180">
              <template #default="{ row }">
                {{ formatTime(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>

          <div class="table-footer" v-if="hasVersions || versionPagination.total > 0">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="versionPagination.pageNo"
              :page-size="versionPagination.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="versionPagination.total"
              @current-change="handleCurrentChange"
              @size-change="handleSizeChange"
            />
          </div>
        </template>
      </section>
    </template>
  </section>
</template>

<style scoped>
.document-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-loading,
.detail-error,
.detail-panel,
.version-panel {
  padding: 24px;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.detail-eyebrow {
  margin: 0 0 8px;
  color: #9b7755;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.head-actions {
  display: flex;
  gap: 12px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.overview-card {
  padding: 22px;
}

.overview-card span,
.detail-item span,
.storage-item span {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.overview-card strong,
.detail-item strong,
.storage-item strong {
  display: block;
  margin-top: 12px;
  font-family: "Noto Serif SC", serif;
  font-size: 22px;
  word-break: break-word;
}

.overview-card p {
  margin: 12px 0 0;
  color: #6d5948;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.section-head h2 {
  margin: 0;
  font-family: "Noto Serif SC", serif;
  font-size: 24px;
}

.section-head p {
  margin: 8px 0 0;
  color: #6d5948;
}

.detail-matrix {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.detail-item,
.storage-item {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 250, 242, 0.72);
}

.storage-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 18px;
}

.version-error {
  padding: 8px 0;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
}

.error-text {
  margin: 0;
  color: #6d5948;
}

.error-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

@media (max-width: 960px) {
  .detail-head,
  .section-head {
    flex-direction: column;
  }

  .head-actions {
    width: 100%;
  }

  .overview-grid,
  .detail-matrix,
  .storage-panel {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .detail-loading,
  .detail-error,
  .detail-panel,
  .version-panel {
    padding: 20px;
  }

  .head-actions,
  .error-actions {
    flex-direction: column;
  }
}
</style>
