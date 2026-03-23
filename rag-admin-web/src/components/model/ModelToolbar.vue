<script setup lang="ts">
import type { ModelProvider } from '@/types/model'
import { capabilityOptions, providerStatusOptions } from '@/utils/model-management'

defineProps<{
  providers: ModelProvider[]
  total: number
  currentPageCount: number
  currentPageEmbeddingCount: number
  currentPageUnsupportedEmbeddingCount: number
  selectedCount: number
  loading: boolean
  batchDeleteSubmitting: boolean
  shouldShowDefaultChatModelAlert: boolean
}>()

const providerCode = defineModel<string>('providerCode', { required: true })
const capabilityType = defineModel<string>('capabilityType', { required: true })
const status = defineModel<string>('status', { required: true })

const emit = defineEmits<{
  search: []
  reset: []
  refresh: []
  openProviderDrawer: []
  openCreateModel: []
  batchDelete: []
}>()
</script>

<template>
  <section class="toolbar-panel soft-panel">
    <div class="toolbar-top">
      <div class="metric-strip">
        <article class="metric-card">
          <small>模型总量</small>
          <strong>{{ total }}</strong>
          <span>当前页 {{ currentPageCount }} 条</span>
        </article>
        <article class="metric-card">
          <small>当前页向量模型</small>
          <strong>{{ currentPageEmbeddingCount }}</strong>
          <span>同步、异步、视觉统一展示</span>
        </article>
        <article class="metric-card" :class="{ 'is-warning': currentPageUnsupportedEmbeddingCount > 0 }">
          <small>当前页受限模型</small>
          <strong>{{ currentPageUnsupportedEmbeddingCount }}</strong>
          <span>异步文本与视觉向量暂不可进入运行链路</span>
        </article>
      </div>
    </div>

    <section class="architecture-banner">
      <div class="banner-code">SYNC ONLY</div>
      <div class="banner-content">
        <strong>当前架构仅支持同步文本向量化操作</strong>
        <p>
          异步文本向量模型与视觉向量模型当前只支持登记、展示和后续扩展预留，不能直接参与知识库文档解析与检索。
        </p>
      </div>
    </section>

    <el-alert
      v-if="shouldShowDefaultChatModelAlert"
      type="error"
      :closable="false"
      show-icon
      title="当前尚未设置默认聊天模型，问答链路将不可用，请先在本页选择一个启用中的聊天模型设为默认。"
      class="default-alert"
    />

    <section class="filter-shell">
      <div class="filter-grid">
        <el-select v-model="providerCode" placeholder="提供方" clearable>
          <el-option
            v-for="item in providers"
            :key="item.id"
            :label="item.providerName"
            :value="item.providerCode"
          />
        </el-select>

        <el-select v-model="capabilityType" placeholder="模型用途" clearable>
          <el-option
            v-for="item in capabilityOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-select v-model="status" placeholder="状态" clearable>
          <el-option
            v-for="item in providerStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>

      <div class="filter-actions">
        <span class="selection-tip" :class="{ 'is-active': selectedCount > 0 }">
          已选择 {{ selectedCount }} 项
        </span>
        <el-button
          type="danger"
          plain
          :disabled="selectedCount === 0"
          :loading="batchDeleteSubmitting"
          @click="emit('batchDelete')"
        >
          批量删除
        </el-button>
        <el-button @click="emit('reset')">重置</el-button>
        <el-button type="primary" :loading="loading" @click="emit('search')">查询模型</el-button>
      </div>

      <div class="toolbar-actions">
        <el-button @click="emit('refresh')">刷新页面</el-button>
        <el-button plain @click="emit('openProviderDrawer')">提供方维护</el-button>
        <el-button type="primary" @click="emit('openCreateModel')">新增模型</el-button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.toolbar-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 22px;
}

.toolbar-top {
  display: block;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  flex: 1;
}

.metric-card {
  padding: 14px 16px;
  border: 1px solid rgba(141, 69, 16, 0.1);
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(255, 252, 246, 0.96), rgba(252, 241, 228, 0.9));
}

.metric-card.is-warning {
  border-color: rgba(181, 77, 61, 0.24);
  background:
    linear-gradient(135deg, rgba(255, 248, 245, 0.98), rgba(255, 237, 228, 0.92));
}

.metric-card small,
.metric-card span {
  display: block;
}

.metric-card small {
  color: #8d6f57;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.metric-card strong {
  display: block;
  margin: 10px 0 6px;
  font-size: 28px;
  line-height: 1;
  color: #7d3e0f;
}

.metric-card span {
  color: #6d5948;
  font-size: 13px;
}

.toolbar-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.architecture-banner {
  display: grid;
  grid-template-columns: 108px minmax(0, 1fr);
  gap: 18px;
  padding: 18px 20px;
  border-radius: 20px;
  background:
    linear-gradient(135deg, rgba(143, 70, 16, 0.95), rgba(198, 107, 34, 0.92));
  color: #fff8f0;
  box-shadow: 0 18px 32px rgba(141, 69, 16, 0.22);
}

.banner-code {
  display: grid;
  place-items: center;
  min-height: 82px;
  border-radius: 16px;
  background: rgba(255, 248, 239, 0.14);
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.22em;
}

.banner-content strong {
  display: block;
  font-size: 20px;
  line-height: 1.3;
}

.banner-content p {
  margin: 8px 0 0;
  color: rgba(255, 248, 240, 0.9);
  line-height: 1.7;
}

.default-alert {
  border-radius: 18px;
}

.filter-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.selection-tip {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(141, 69, 16, 0.08);
  color: #8f735b;
  font-size: 13px;
}

.selection-tip.is-active {
  background: rgba(198, 107, 34, 0.16);
  color: #8d4510;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .metric-strip,
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar-actions {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .toolbar-panel {
    padding: 18px;
  }

  .metric-strip,
  .filter-grid,
  .architecture-banner {
    grid-template-columns: 1fr;
  }

  .filter-actions {
    justify-content: stretch;
  }

  .filter-actions :deep(.el-button) {
    flex: 1 1 140px;
  }

  .selection-tip {
    width: 100%;
    justify-content: center;
  }
}
</style>
