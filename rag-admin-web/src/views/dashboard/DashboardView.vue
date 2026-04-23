<script setup lang="ts">
import { computed } from 'vue'
import { Collection, Connection, List, Plus, Tickets } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { hasPermission } from '@/utils/permission'

const router = useRouter()
const authStore = useAuthStore()

const quickEntries = [
  {
    key: 'knowledge-bases',
    eyebrow: '知识库',
    title: '进入知识库管理',
    description: '查看知识库列表，进入详情、编辑和文档管理。',
    actionText: '打开列表',
    path: '/knowledge-bases',
    icon: Collection,
    accent: 'is-light',
    permission: 'KB_MANAGE',
  },
  {
    key: 'knowledge-base-create',
    eyebrow: '创建',
    title: '新建知识库',
    description: '直接开始创建新的知识库并配置模型与检索参数。',
    actionText: '立即创建',
    path: '/knowledge-bases/create',
    icon: Plus,
    accent: 'is-warm',
    permission: 'KB_MANAGE',
  },
  {
    key: 'models',
    eyebrow: '模型',
    title: '进入模型管理',
    description: '维护模型配置、提供方接入信息和探活结果。',
    actionText: '管理模型',
    path: '/models',
    icon: Connection,
    accent: 'is-light',
    permission: 'MODEL_MANAGE',
  },
  {
    key: 'tasks',
    eyebrow: '任务',
    title: '查看任务监控',
    description: '快速检查解析任务状态、错误摘要和重试入口。',
    actionText: '查看任务',
    path: '/tasks',
    icon: List,
    accent: 'is-light',
    permission: 'TASK_VIEW',
  },
  {
    key: 'audit-logs',
    eyebrow: '治理',
    title: '进入审计日志',
    description: '查看管理员操作轨迹，并快速筛选问答反馈类审计记录。',
    actionText: '查看审计',
    path: '/audit-logs',
    icon: Tickets,
    accent: 'is-light',
    permission: 'AUDIT_VIEW',
  },
]

const visibleEntries = computed(() => {
  return quickEntries.filter((entry) => hasPermission(authStore.currentUser, entry.permission))
})

async function goTo(path: string): Promise<void> {
  await router.push(path)
}
</script>

<template>
  <section class="dashboard-page">
    <header class="dashboard-head soft-panel">
      <div>
        <h1 class="page-title">概览</h1>
      </div>
    </header>

    <section class="entry-grid">
      <button
        v-for="entry in visibleEntries"
        :key="entry.key"
        type="button"
        class="entry-card soft-panel"
        :class="entry.accent"
        @click="goTo(entry.path)"
      >
        <div class="entry-top">
          <div class="entry-icon">
            <el-icon><component :is="entry.icon" /></el-icon>
          </div>
          <span class="entry-link">{{ entry.actionText }}</span>
        </div>
        <div class="entry-copy">
          <span class="entry-eyebrow">{{ entry.eyebrow }}</span>
          <strong>{{ entry.title }}</strong>
          <p>{{ entry.description }}</p>
        </div>
      </button>
    </section>
  </section>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.dashboard-head {
  position: relative;
  overflow: hidden;
  padding: 12px 20px;
}

.dashboard-eyebrow,
.entry-eyebrow {
  color: var(--ember-neutral);
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.dashboard-eyebrow {
  margin: 0 0 10px;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 20px;
}

.entry-card {
  position: relative;
  display: flex;
  flex-direction: column;
  grid-column: span 6;
  gap: 20px;
  min-height: 0;
  padding: 16px 20px;
  border: 1px solid var(--ember-border);
  background: var(--ember-surface);
  border-radius: var(--ember-radius-lg);
  text-align: left;
  cursor: pointer;
  transition:
    transform 150ms ease,
    box-shadow 150ms ease,
    border-color 150ms ease;
}

.entry-card.is-primary {
  background:
    radial-gradient(circle at top right, rgba(194, 65, 12, 0.08), transparent 34%),
    var(--ember-surface);
}

.entry-card.is-warm {
  background:
    radial-gradient(circle at bottom left, rgba(194, 65, 12, 0.04), transparent 50%),
    var(--ember-surface);
}

.entry-card.is-light {
  background: var(--ember-surface);
}

.entry-card:hover {
  transform: translateY(-2px);
  border-color: var(--ember-primary);
  box-shadow: var(--ember-shadow-md);
}

.entry-card:nth-child(1),
.entry-card:nth-child(2) {
  min-height: 0;
}

.entry-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.entry-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: var(--ember-radius-lg);
  background: var(--ember-primary-light);
  color: var(--ember-primary);
  font-size: 22px;
}

.entry-link {
  color: var(--ember-primary);
  font-size: 13px;
  font-weight: 600;
}

.entry-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.entry-copy strong {
  font-family: var(--ember-font-heading);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.entry-copy p {
  margin: 0;
  color: var(--ember-text-secondary);
  line-height: 1.7;
}

@media (max-width: 960px) {
  .entry-grid {
    grid-template-columns: 1fr;
  }

  .entry-card {
    grid-column: auto;
  }
}

@media (max-width: 640px) {
  .dashboard-head,
  .entry-card {
    padding: 12px 16px;
  }
}
</style>
