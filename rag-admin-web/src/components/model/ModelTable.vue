<script setup lang="ts">
import type { ModelDefinition, ModelHealthCheck } from '@/types/model'
import {
  capabilityLabel,
  detectModelScene,
  isArchitectureReady,
  isChatModel,
  runtimeOptionDisplay,
  sceneDescription,
  sceneLabel,
  sceneTagType,
  statusTagType,
} from '@/utils/model-management'

defineProps<{
  models: ModelDefinition[]
  loading: boolean
  modelLoadError: string
  checkingIds: number[]
  defaultChatModelLoadingId: number | null
  healthResult: ModelHealthCheck | null
  activeHealthId: number | null
  pagination: {
    pageNo: number
    pageSize: number
    total: number
  }
}>()

const emit = defineEmits<{
  edit: [model: ModelDefinition]
  healthCheck: [model: ModelDefinition]
  setDefaultChatModel: [model: ModelDefinition]
  delete: [model: ModelDefinition]
  selectionChange: [selection: ModelDefinition[]]
  currentChange: [pageNo: number]
  sizeChange: [pageSize: number]
}>()

function rowChecking(modelId: number, checkingIds: number[]): boolean {
  return checkingIds.includes(modelId)
}

function resolveScene(model: ModelDefinition) {
  return detectModelScene(model.modelType, model.modelCode)
}

function resolvePrimaryCapabilityLabel(model: ModelDefinition): string {
  if (model.modelType === 'RERANKER' || model.capabilityTypes?.includes('RERANK')) return '重排'
  return isChatModel(model) ? '聊天' : '向量化'
}

function isUnsupportedEmbedding(model: ModelDefinition): boolean {
  return model.modelType === 'EMBEDDING' && !isArchitectureReady(resolveScene(model))
}
</script>

<template>
  <section class="table-panel soft-panel">
    <section v-if="modelLoadError" class="inline-error">
      {{ modelLoadError }}
    </section>

    <section v-if="!loading && healthResult" class="health-banner">
      <div class="health-banner-head">
        <div>
          <strong>
            最近一次模型探活
            <template v-if="activeHealthId">
              / #{{ activeHealthId }}
            </template>
          </strong>
          <p>{{ healthResult.message }}</p>
        </div>
        <el-tag :type="statusTagType(healthResult.status)">
          {{ healthResult.status }}
        </el-tag>
      </div>

      <div class="health-detail-list">
        <article
          v-for="item in healthResult.capabilityChecks"
          :key="`${healthResult.modelId}-${item.capabilityType}`"
          class="health-detail-item"
        >
          <el-tag :type="statusTagType(item.status)">
            {{ capabilityLabel(item.capabilityType) }} / {{ item.status }}
          </el-tag>
          <p>{{ item.message }}</p>
        </article>
      </div>
    </section>

    <section v-if="!loading && !models.length" class="empty-state">
      <strong>当前没有可展示的模型定义</strong>
      <p>可以先新增模型，或调整筛选条件后重新查询。</p>
    </section>

    <section v-if="models.length || loading">
      <el-table
        :data="models"
        v-loading="loading"
        empty-text="暂无模型定义"
        stripe
        @selection-change="emit('selectionChange', $event)"
      >
        <el-table-column type="selection" width="52" />

        <el-table-column label="模型" min-width="280">
          <template #default="{ row }">
            <div class="model-cell">
              <div class="model-main">
                <strong>{{ row.modelName }}</strong>
                <el-tag
                  v-if="row.isDefaultChatModel"
                  effect="plain"
                  type="warning"
                  class="default-tag"
                >
                  默认聊天模型
                </el-tag>
              </div>
              <small>{{ row.modelCode }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="提供方" min-width="150">
          <template #default="{ row }">
            <div class="provider-cell">
              <strong>{{ row.providerName || row.providerCode || '未知' }}</strong>
              <small>{{ row.providerCode || '未配置编码' }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="用途与模式" min-width="260">
          <template #default="{ row }">
            <div class="scene-cell">
              <div class="scene-tags">
                <el-tag effect="plain">
                  {{ resolvePrimaryCapabilityLabel(row) }}
                </el-tag>
                <el-tag
                  :type="sceneTagType(resolveScene(row))"
                  effect="dark"
                >
                  {{ sceneLabel(resolveScene(row)) }}
                </el-tag>
              </div>
              <p
                class="scene-desc"
                :class="{ 'is-warning': isUnsupportedEmbedding(row) }"
              >
                {{ sceneDescription(resolveScene(row)) }}
              </p>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="运行参数" min-width="160">
          <template #default="{ row }">
            <div class="runtime-cell">
              <span>最大令牌数：{{ runtimeOptionDisplay(row.modelType, row.maxTokens) }}</span>
              <span>默认温度：{{ runtimeOptionDisplay(row.modelType, row.temperatureDefault) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <div class="action-links">
              <el-button link type="primary" @click="emit('edit', row)">编辑</el-button>
              <el-button
                link
                type="primary"
                :loading="rowChecking(row.id, checkingIds)"
                @click="emit('healthCheck', row)"
              >
                探活
              </el-button>
              <el-button
                v-if="isChatModel(row)"
                link
                :type="row.isDefaultChatModel ? 'warning' : 'primary'"
                :loading="defaultChatModelLoadingId === row.id"
                :disabled="row.status !== 'ENABLED' || row.isDefaultChatModel"
                @click="emit('setDefaultChatModel', row)"
              >
                {{ row.isDefaultChatModel ? '默认中' : '设为默认' }}
              </el-button>
              <el-button link type="danger" @click="emit('delete', row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer" v-if="models.length || pagination.total > 0">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pagination.pageNo"
          :page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="emit('currentChange', $event)"
          @size-change="emit('sizeChange', $event)"
        />
      </div>
    </section>
  </section>
</template>

<style scoped>
.table-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 22px;
}

.health-banner {
  padding: 18px 20px;
  border: 1px solid var(--ember-success);
  border-radius: var(--ember-radius-lg);
  background: rgba(22, 163, 74, 0.08);
}

.health-banner-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.health-banner-head strong {
  display: block;
  font-size: 16px;
}

.health-banner-head p {
  margin: 8px 0 0;
  color: var(--ember-text-secondary);
}

.health-detail-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.health-detail-item {
  padding: 14px 16px;
  border-radius: var(--ember-radius-md);
  background: rgba(255, 255, 255, 0.72);
}

.health-detail-item p {
  margin: 10px 0 0;
  color: var(--ember-text-secondary);
  line-height: 1.6;
}

.empty-state {
  display: grid;
  place-items: center;
  min-height: 220px;
  border: 1px dashed var(--ember-primary-medium);
  border-radius: var(--ember-radius-lg);
  background: var(--ember-surface);
  text-align: center;
}

.empty-state strong {
  font-size: 18px;
}

.empty-state p {
  margin: 8px 0 0;
  color: var(--ember-text-secondary);
}

.inline-error {
  padding: 14px 16px;
  border-radius: var(--ember-radius-md);
  background: var(--ember-error);
  color: var(--ember-error);
}

.model-cell,
.provider-cell,
.scene-cell,
.runtime-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.model-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.model-cell small,
.provider-cell small {
  color: var(--ember-text-muted);
  font-size: 12px;
}

.default-tag {
  border-radius: var(--ember-radius-pill);
}

.scene-tags,
.action-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.scene-desc {
  margin: 0;
  color: var(--ember-text-secondary);
  line-height: 1.6;
}

.scene-desc.is-warning {
  color: var(--ember-error);
  font-weight: 600;
}

.runtime-cell span {
  color: var(--ember-text-secondary);
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

@media (max-width: 720px) {
  .table-panel {
    padding: 18px;
  }

  .health-banner-head {
    flex-direction: column;
  }
}
</style>
