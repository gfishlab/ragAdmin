<script setup lang="ts">
import { Collection, Connection, List, Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const quickEntries = [
  {
    key: 'knowledge-bases',
    eyebrow: '知识库',
    title: '进入知识库管理',
    description: '查看知识库列表，进入详情、编辑和文档管理。',
    actionText: '打开列表',
    path: '/knowledge-bases',
    icon: Collection,
    accent: 'is-primary',
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
  },
]

async function goTo(path: string): Promise<void> {
  await router.push(path)
}
</script>

<template>
  <section class="dashboard-page">
    <header class="dashboard-head soft-panel">
      <div>
        <p class="dashboard-eyebrow">快捷入口</p>
        <h1 class="page-title">从这里直接进入常用操作</h1>
        <p class="page-subtitle">
          首页只保留高频入口，减少跳转前的阅读成本。
        </p>
      </div>
    </header>

    <section class="entry-grid">
      <article
        v-for="entry in quickEntries"
        :key="entry.key"
        class="entry-card soft-panel"
        :class="entry.accent"
      >
        <div class="entry-icon">
          <el-icon><component :is="entry.icon" /></el-icon>
        </div>
        <div class="entry-copy">
          <span class="entry-eyebrow">{{ entry.eyebrow }}</span>
          <strong>{{ entry.title }}</strong>
          <p>{{ entry.description }}</p>
        </div>
        <el-button
          class="entry-action"
          :type="entry.accent === 'is-primary' ? 'primary' : 'default'"
          @click="goTo(entry.path)"
        >
          {{ entry.actionText }}
        </el-button>
      </article>
    </section>
  </section>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dashboard-head {
  padding: 24px 28px;
}

.dashboard-eyebrow,
.entry-eyebrow {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.dashboard-eyebrow {
  margin: 0 0 10px;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.entry-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 240px;
  padding: 26px;
}

.entry-card.is-primary {
  background:
    radial-gradient(circle at top right, rgba(211, 120, 41, 0.18), transparent 34%),
    rgba(255, 249, 241, 0.92);
}

.entry-card.is-warm {
  background:
    linear-gradient(135deg, rgba(255, 245, 232, 0.92), rgba(255, 251, 246, 0.92));
}

.entry-card.is-light {
  background: rgba(255, 251, 246, 0.88);
}

.entry-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: rgba(198, 107, 34, 0.12);
  color: #8d4510;
  font-size: 22px;
}

.entry-copy {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.entry-copy strong {
  font-family: "Noto Serif SC", serif;
  font-size: 28px;
  line-height: 1.2;
}

.entry-copy p {
  margin: 0;
  color: #6d5948;
  line-height: 1.7;
}

.entry-action {
  align-self: flex-start;
  margin-top: auto;
}

@media (max-width: 960px) {
  .entry-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .dashboard-head,
  .entry-card {
    padding: 20px;
  }

  .entry-action {
    width: 100%;
  }
}
</style>
