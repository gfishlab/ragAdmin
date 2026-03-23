<script setup lang="ts">
import type { ModelProvider, ModelProviderHealthCheck } from '@/types/model'
import { capabilityLabel, statusTagType } from '@/utils/model-management'

defineProps<{
  providers: ModelProvider[]
  loading: boolean
  checkingIds: number[]
  healthResult: ModelProviderHealthCheck | null
  activeHealthId: number | null
}>()

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  refresh: []
  openCreate: []
  healthCheck: [provider: ModelProvider]
}>()

function providerChecking(providerId: number, checkingIds: number[]): boolean {
  return checkingIds.includes(providerId)
}
</script>

<template>
  <el-drawer
    v-model="visible"
    title="提供方维护"
    size="760px"
    :with-header="true"
    class="provider-drawer"
  >
    <div class="drawer-shell">
      <section class="drawer-tip">
        <strong>提供方维护保留在次级入口</strong>
        <p>这里集中管理模型接入地址、密钥引用和连通性，避免影响模型定义的主流程操作。</p>
      </section>

      <div class="drawer-actions">
        <el-button @click="emit('refresh')">刷新</el-button>
        <el-button type="primary" @click="emit('openCreate')">新增提供方</el-button>
      </div>

      <el-table :data="providers" v-loading="loading" empty-text="暂无模型提供方" stripe>
        <el-table-column prop="providerCode" label="提供方编码" min-width="140" />
        <el-table-column prop="providerName" label="提供方名称" min-width="150" />
        <el-table-column label="接入地址" min-width="220">
          <template #default="{ row }">
            {{ row.baseUrl || '未配置' }}
          </template>
        </el-table-column>
        <el-table-column label="密钥引用" min-width="180">
          <template #default="{ row }">
            {{ row.apiKeySecretRef || '未配置' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="providerChecking(row.id, checkingIds)"
              @click="emit('healthCheck', row)"
            >
              探活
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <section v-if="healthResult" class="health-result">
        <div class="health-header">
          <div>
            <strong>
              最近一次提供方探活
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
            :key="`${healthResult.providerId}-${item.capabilityType}`"
            class="health-detail-item"
          >
            <div class="health-detail-head">
              <el-tag :type="statusTagType(item.status)">
                {{ capabilityLabel(item.capabilityType) }} / {{ item.status }}
              </el-tag>
              <span>{{ item.modelCode ? `模型 ${item.modelCode}` : '当前无可用模型' }}</span>
            </div>
            <p>{{ item.message }}</p>
          </article>
        </div>
      </section>
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.drawer-tip {
  padding: 18px 20px;
  border-radius: 18px;
  background: rgba(255, 247, 237, 0.92);
  border: 1px solid rgba(198, 107, 34, 0.16);
}

.drawer-tip strong {
  display: block;
  font-size: 16px;
}

.drawer-tip p {
  margin: 8px 0 0;
  color: #6d5948;
  line-height: 1.7;
}

.drawer-actions,
.health-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.health-result {
  padding: 18px 20px;
  border-radius: 20px;
  background: rgba(247, 250, 255, 0.86);
  border: 1px solid rgba(88, 126, 171, 0.14);
}

.health-header p {
  margin: 8px 0 0;
  color: #61708a;
}

.health-detail-list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.health-detail-item {
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.74);
}

.health-detail-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.health-detail-item span,
.health-detail-item p {
  color: #635b54;
}

.health-detail-item p {
  margin: 10px 0 0;
  line-height: 1.6;
}

@media (max-width: 720px) {
  .drawer-actions,
  .health-header {
    flex-direction: column;
  }
}
</style>
