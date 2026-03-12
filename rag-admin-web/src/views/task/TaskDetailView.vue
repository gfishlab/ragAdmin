<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElButton, ElEmpty, ElSkeleton } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getTaskDetail } from '@/api/task'
import { resolveErrorMessage } from '@/api/http'
import type { TaskDetail } from '@/types/task'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const detail = ref<TaskDetail | null>(null)

const taskId = computed(() => Number(route.params.id))

function taskStatusType(status: string): 'warning' | 'success' | 'danger' | 'info' {
  if (status === 'WAITING' || status === 'RUNNING') {
    return 'warning'
  }
  if (status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

function formatTime(value: string): string {
  if (!value) {
    return '暂无时间'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    hour12: false,
  })
}

async function loadDetail(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await getTaskDetail(taskId.value)
  } catch (error) {
    detail.value = null
    loadError.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function handleBack(): Promise<void> {
  await router.push('/tasks')
}

onMounted(async () => {
  await loadDetail()
})
</script>

<template>
  <section class="task-detail-page">
    <el-skeleton v-if="loading" :rows="10" animated class="detail-loading soft-panel" />

    <section v-else-if="loadError" class="detail-error soft-panel">
      <el-empty description="任务详情加载失败">
        <template #description>
          <p class="error-text">{{ loadError }}</p>
        </template>
        <div class="error-actions">
          <el-button @click="loadDetail">重新加载</el-button>
          <el-button type="primary" @click="handleBack">返回任务列表</el-button>
        </div>
      </el-empty>
    </section>

    <template v-else-if="detail">
      <header class="detail-head">
        <div>
          <p class="detail-eyebrow">Task / Detail</p>
          <h1 class="page-title">任务 #{{ detail.taskId }}</h1>
          <p class="page-subtitle">
            当前页面聚焦任务基础信息、错误信息和时间信息，不提前扩展状态流转时间线。
          </p>
        </div>
        <div class="head-actions">
          <el-button @click="loadDetail">刷新详情</el-button>
          <el-button type="primary" @click="handleBack">返回任务列表</el-button>
        </div>
      </header>

      <section class="overview-grid">
        <article class="overview-card soft-panel">
          <span>任务类型</span>
          <strong>{{ detail.taskType }}</strong>
          <p>用于区分文档解析、索引构建等异步任务类别。</p>
        </article>
        <article class="overview-card soft-panel">
          <span>任务状态</span>
          <strong>
            <el-tag :type="taskStatusType(detail.taskStatus)">{{ detail.taskStatus }}</el-tag>
          </strong>
          <p>以后台真实任务状态为准，前端不对状态流转做推断。</p>
        </article>
        <article class="overview-card soft-panel">
          <span>业务 ID</span>
          <strong>{{ detail.bizId ?? '暂无' }}</strong>
          <p>当前版本先只展示业务 ID，不强行推断业务跳转目标。</p>
        </article>
      </section>

      <section class="detail-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>任务详情</h2>
            <p>首版任务详情页聚焦基础字段与错误信息完整呈现。</p>
          </div>
        </div>

        <div class="detail-matrix">
          <article class="detail-item">
            <span>任务 ID</span>
            <strong>{{ detail.taskId }}</strong>
          </article>
          <article class="detail-item">
            <span>业务类型</span>
            <strong>{{ detail.bizType || '暂无' }}</strong>
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
      </section>

      <section class="error-panel soft-panel">
        <div class="section-head">
          <div>
            <h2>错误信息</h2>
            <p>若任务失败，可在此查看完整错误内容；成功任务通常为空。</p>
          </div>
        </div>
        <pre class="error-block">{{ detail.errorMessage || '当前任务暂无错误信息。' }}</pre>
      </section>
    </template>
  </section>
</template>

<style scoped>
.task-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-loading,
.detail-error,
.detail-panel,
.error-panel {
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
.detail-item span {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.overview-card strong,
.detail-item strong {
  display: block;
  margin-top: 12px;
  font-family: "Noto Serif SC", serif;
  font-size: 24px;
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

.detail-item {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 250, 242, 0.72);
}

.error-block {
  margin: 0;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 250, 242, 0.72);
  color: #5d4736;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "Consolas", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.6;
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
  .detail-matrix {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .detail-loading,
  .detail-error,
  .detail-panel,
  .error-panel {
    padding: 20px;
  }

  .head-actions,
  .error-actions {
    flex-direction: column;
  }
}
</style>
