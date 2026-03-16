<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  ModelCreateRequest,
  ModelDefinition,
  ModelHealthCheck,
  ModelProvider,
  ModelProviderCreateRequest,
  ModelProviderHealthCheck,
} from '@/types/model'
import {
  createModel,
  createModelProvider,
  healthCheckModel,
  healthCheckModelProvider,
  listModelProviders,
  listModels,
} from '@/api/model'
import { resolveErrorMessage } from '@/api/http'

const providerLoading = ref(false)
const providerSubmitting = ref(false)
const providerDialogVisible = ref(false)
const providerCheckingIds = ref<number[]>([])
const providers = ref<ModelProvider[]>([])
const providerHealthResult = ref<ModelProviderHealthCheck | null>(null)

const modelLoading = ref(false)
const modelSubmitting = ref(false)
const modelDialogVisible = ref(false)
const modelCheckingIds = ref<number[]>([])
const models = ref<ModelDefinition[]>([])
const modelHealthResult = ref<ModelHealthCheck | null>(null)
const modelLoadError = ref('')

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
})

const modelQuery = reactive({
  providerCode: '',
  capabilityType: '',
  status: '',
})

const providerForm = reactive<ModelProviderCreateRequest>({
  providerCode: '',
  providerName: '',
  baseUrl: '',
  apiKeySecretRef: '',
  status: 'ENABLED',
})

const modelForm = reactive<ModelCreateRequest>({
  providerId: null,
  modelCode: '',
  modelName: '',
  capabilityTypes: [],
  modelType: 'CHAT',
  maxTokens: null,
  temperatureDefault: 0.7,
  status: 'ENABLED',
})

const providerStatusOptions = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
]

const providerCreateStatusOptions = providerStatusOptions.filter((item) => item.value)

const capabilityOptions = [
  { label: '全部能力', value: '' },
  { label: '文本生成', value: 'TEXT_GENERATION' },
  { label: '向量生成', value: 'EMBEDDING' },
]

const modelTypeOptions = [
  { label: '聊天模型', value: 'CHAT' },
  { label: '向量模型', value: 'EMBEDDING' },
]

const modelCapabilityCreateOptions = capabilityOptions.filter((item) => item.value)

const hasModelData = computed(() => models.value.length > 0)

function statusTagType(status: string): 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'ENABLED' || status === 'UP' || status === 'SUCCESS') {
    return 'success'
  }
  if (status === 'DISABLED' || status === 'UNKNOWN') {
    return 'info'
  }
  if (status === 'DOWN' || status === 'FAILED') {
    return 'danger'
  }
  return 'warning'
}

function providerChecking(providerId: number): boolean {
  return providerCheckingIds.value.includes(providerId)
}

function modelChecking(modelId: number): boolean {
  return modelCheckingIds.value.includes(modelId)
}

function capabilityLabel(capability: string): string {
  if (capability === 'TEXT_GENERATION') {
    return '文本生成'
  }
  if (capability === 'EMBEDDING') {
    return '向量生成'
  }
  return capability
}

function modelTypeLabel(modelType: string): string {
  if (modelType === 'CHAT') {
    return '聊天模型'
  }
  if (modelType === 'EMBEDDING') {
    return '向量模型'
  }
  return modelType
}

function providerNameById(providerId: number | null): string {
  if (!providerId) {
    return '未绑定'
  }
  return providers.value.find((item) => item.id === providerId)?.providerName ?? `#${providerId}`
}

function resetProviderForm(): void {
  providerForm.providerCode = ''
  providerForm.providerName = ''
  providerForm.baseUrl = ''
  providerForm.apiKeySecretRef = ''
  providerForm.status = 'ENABLED'
}

function resetModelForm(): void {
  modelForm.providerId = null
  modelForm.modelCode = ''
  modelForm.modelName = ''
  modelForm.capabilityTypes = []
  modelForm.modelType = 'CHAT'
  modelForm.maxTokens = null
  modelForm.temperatureDefault = 0.7
  modelForm.status = 'ENABLED'
}

async function loadProviders(): Promise<void> {
  providerLoading.value = true
  try {
    providers.value = await listModelProviders()
  } catch (error) {
    providers.value = []
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    providerLoading.value = false
  }
}

async function loadModels(): Promise<void> {
  modelLoading.value = true
  modelLoadError.value = ''
  try {
    const response = await listModels({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      providerCode: modelQuery.providerCode || undefined,
      capabilityType: modelQuery.capabilityType || undefined,
      status: modelQuery.status || undefined,
    })
    models.value = response.list
    pagination.total = response.total
  } catch (error) {
    models.value = []
    pagination.total = 0
    modelLoadError.value = resolveErrorMessage(error)
  } finally {
    modelLoading.value = false
  }
}

async function loadPageData(): Promise<void> {
  await Promise.all([loadProviders(), loadModels()])
}

async function handleProviderHealthCheck(provider: ModelProvider): Promise<void> {
  providerCheckingIds.value = [...providerCheckingIds.value, provider.id]
  try {
    providerHealthResult.value = await healthCheckModelProvider(provider.id)
    ElMessage.success(`${provider.providerName} 探活完成`)
  } catch (error) {
    providerHealthResult.value = null
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    providerCheckingIds.value = providerCheckingIds.value.filter((id) => id !== provider.id)
  }
}

async function handleModelHealthCheck(model: ModelDefinition): Promise<void> {
  modelCheckingIds.value = [...modelCheckingIds.value, model.id]
  try {
    modelHealthResult.value = await healthCheckModel(model.id)
    ElMessage.success(`${model.modelName} 探活完成`)
  } catch (error) {
    modelHealthResult.value = null
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    modelCheckingIds.value = modelCheckingIds.value.filter((id) => id !== model.id)
  }
}

async function handleCreateProvider(): Promise<void> {
  providerSubmitting.value = true
  try {
    await createModelProvider({
      ...providerForm,
      baseUrl: providerForm.baseUrl?.trim() || null,
      apiKeySecretRef: providerForm.apiKeySecretRef?.trim() || null,
    })
    providerDialogVisible.value = false
    resetProviderForm()
    await loadProviders()
    ElMessage.success('模型提供方创建成功')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    providerSubmitting.value = false
  }
}

async function handleCreateModel(): Promise<void> {
  modelSubmitting.value = true
  try {
    await createModel({
      ...modelForm,
      modelCode: modelForm.modelCode.trim(),
      modelName: modelForm.modelName.trim(),
      maxTokens: modelForm.maxTokens || null,
      temperatureDefault: modelForm.temperatureDefault ?? null,
    })
    modelDialogVisible.value = false
    resetModelForm()
    await loadModels()
    ElMessage.success('模型创建成功')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error))
  } finally {
    modelSubmitting.value = false
  }
}

async function handleSearchModels(): Promise<void> {
  pagination.pageNo = 1
  await loadModels()
}

async function handleResetModels(): Promise<void> {
  modelQuery.providerCode = ''
  modelQuery.capabilityType = ''
  modelQuery.status = ''
  pagination.pageNo = 1
  await loadModels()
}

async function handleCurrentChange(pageNo: number): Promise<void> {
  pagination.pageNo = pageNo
  await loadModels()
}

async function handleSizeChange(pageSize: number): Promise<void> {
  pagination.pageSize = pageSize
  pagination.pageNo = 1
  await loadModels()
}

onMounted(async () => {
  await loadPageData()
})
</script>

<template>
  <section class="model-page">
    <header class="model-head">
      <div>
        <h1 class="page-title">模型管理</h1>
        <p class="page-subtitle">
          当前页面用于集中管理模型提供方与模型定义，先支持列表、筛选、探活和新增，保证联调闭环最短。
        </p>
      </div>
      <div class="head-actions">
        <el-button @click="loadPageData">刷新页面</el-button>
        <el-button type="primary" @click="providerDialogVisible = true">新增提供方</el-button>
        <el-button type="primary" plain @click="modelDialogVisible = true">新增模型</el-button>
      </div>
    </header>

    <div class="summary-strip">
      <article class="summary-card soft-panel">
        <span>提供方数量</span>
        <strong>{{ providers.length }}</strong>
        <p>当前已接入的模型提供方总量。</p>
      </article>
      <article class="summary-card soft-panel">
        <span>模型总量</span>
        <strong>{{ pagination.total }}</strong>
        <p>模型分页接口返回的真实总量。</p>
      </article>
      <article class="summary-card soft-panel">
        <span>当前目标</span>
        <strong>联调闭环</strong>
        <p>先把提供方、模型和探活入口统一收敛到一个管理页。</p>
      </article>
    </div>

    <section class="provider-panel soft-panel">
      <div class="section-head">
        <div>
          <h2 class="section-title">模型提供方</h2>
          <p class="section-subtitle">查看当前接入方，支持单条探活与最小新增能力。</p>
        </div>
      </div>

      <el-table :data="providers" v-loading="providerLoading" empty-text="暂无模型提供方" stripe>
        <el-table-column prop="providerCode" label="Provider Code" min-width="150" />
        <el-table-column prop="providerName" label="提供方名称" min-width="160" />
        <el-table-column label="接入地址" min-width="240">
          <template #default="{ row }">
            {{ row.baseUrl || '未配置' }}
          </template>
        </el-table-column>
        <el-table-column label="密钥引用" min-width="180">
          <template #default="{ row }">
            {{ row.apiKeySecretRef || '未配置' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="providerChecking(row.id)"
              @click="handleProviderHealthCheck(row)"
            >
              探活
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <section v-if="providerHealthResult" class="health-result">
        <div class="health-header">
          <strong>最近一次提供方探活结果</strong>
          <el-tag :type="statusTagType(providerHealthResult.status)">
            {{ providerHealthResult.status }}
          </el-tag>
        </div>
        <p class="health-message">{{ providerHealthResult.message }}</p>
        <div class="health-tags">
          <el-tag
            v-for="item in providerHealthResult.capabilityChecks"
            :key="`${providerHealthResult.providerId}-${item.capabilityType}`"
            :type="statusTagType(item.status)"
          >
            {{ capabilityLabel(item.capabilityType) }} / {{ item.status }}
          </el-tag>
        </div>
      </section>
    </section>

    <section class="model-panel soft-panel">
      <div class="section-head">
        <div>
          <h2 class="section-title">模型定义</h2>
          <p class="section-subtitle">支持按提供方、能力和状态筛选，优先保证探活与新增闭环。</p>
        </div>
      </div>

      <section class="filter-panel">
        <div class="filter-grid">
          <el-select v-model="modelQuery.providerCode" placeholder="提供方" clearable>
            <el-option
              v-for="item in providers"
              :key="item.id"
              :label="item.providerName"
              :value="item.providerCode"
            />
          </el-select>

          <el-select v-model="modelQuery.capabilityType" placeholder="能力类型" clearable>
            <el-option
              v-for="item in capabilityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>

          <el-select v-model="modelQuery.status" placeholder="状态" clearable>
            <el-option
              v-for="item in providerStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>

        <div class="filter-actions">
          <el-button @click="handleResetModels">重置</el-button>
          <el-button type="primary" @click="handleSearchModels">查询模型</el-button>
        </div>
      </section>

      <section v-if="modelLoadError" class="inline-error">
        {{ modelLoadError }}
      </section>

      <el-table :data="models" v-loading="modelLoading" empty-text="暂无模型定义" stripe>
        <el-table-column prop="modelCode" label="模型编码" min-width="180" />
        <el-table-column prop="modelName" label="模型名称" min-width="180" />
        <el-table-column label="提供方" min-width="140">
          <template #default="{ row }">
            {{ row.providerName || row.providerCode || '未知' }}
          </template>
        </el-table-column>
        <el-table-column label="能力类型" min-width="200">
          <template #default="{ row }">
            <div class="capability-list">
              <el-tag
                v-for="capability in row.capabilityTypes"
                :key="`${row.id}-${capability}`"
                effect="plain"
              >
                {{ capabilityLabel(capability) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="模型类型" width="120">
          <template #default="{ row }">
            {{ modelTypeLabel(row.modelType) }}
          </template>
        </el-table-column>
        <el-table-column label="Max Tokens" width="120">
          <template #default="{ row }">
            {{ row.maxTokens ?? '未配置' }}
          </template>
        </el-table-column>
        <el-table-column label="默认温度" width="120">
          <template #default="{ row }">
            {{ row.temperatureDefault ?? '未配置' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="modelChecking(row.id)"
              @click="handleModelHealthCheck(row)"
            >
              探活
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer" v-if="hasModelData || pagination.total > 0">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pagination.pageNo"
          :page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>

      <section v-if="modelHealthResult" class="health-result">
        <div class="health-header">
          <strong>最近一次模型探活结果</strong>
          <el-tag :type="statusTagType(modelHealthResult.status)">
            {{ modelHealthResult.status }}
          </el-tag>
        </div>
        <p class="health-message">{{ modelHealthResult.message }}</p>
        <div class="health-tags">
          <el-tag
            v-for="item in modelHealthResult.capabilityChecks"
            :key="`${modelHealthResult.modelId}-${item.capabilityType}`"
            :type="statusTagType(item.status)"
          >
            {{ capabilityLabel(item.capabilityType) }} / {{ item.status }}
          </el-tag>
        </div>
      </section>
    </section>

    <el-dialog v-model="providerDialogVisible" title="新增模型提供方" width="560px">
      <el-form label-position="top">
        <el-form-item label="Provider Code">
          <el-input v-model="providerForm.providerCode" placeholder="例如 BAILIAN / OLLAMA / OPENAI" />
        </el-form-item>
        <el-form-item label="提供方名称">
          <el-input v-model="providerForm.providerName" placeholder="请输入提供方名称" />
        </el-form-item>
        <el-form-item label="接入地址">
          <el-input v-model="providerForm.baseUrl" placeholder="可为空，例如 https://dashscope.aliyuncs.com" />
        </el-form-item>
        <el-form-item label="密钥引用">
          <el-input v-model="providerForm.apiKeySecretRef" placeholder="可为空，例如 secret/bailian/api-key" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="providerForm.status">
            <el-radio
              v-for="item in providerCreateStatusOptions"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="providerDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="providerSubmitting" @click="handleCreateProvider">
            确认创建
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="modelDialogVisible" title="新增模型定义" width="620px">
      <el-form label-position="top">
        <el-form-item label="提供方">
          <el-select v-model="modelForm.providerId" placeholder="请选择模型提供方">
            <el-option
              v-for="item in providers"
              :key="item.id"
              :label="item.providerName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型编码">
          <el-input v-model="modelForm.modelCode" placeholder="例如 qwen-max / text-embedding-v3" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="modelForm.modelName" placeholder="请输入模型展示名称" />
        </el-form-item>
        <el-form-item label="能力类型">
          <el-checkbox-group v-model="modelForm.capabilityTypes">
            <el-checkbox
              v-for="item in modelCapabilityCreateOptions"
              :key="item.value"
              :label="item.value"
            >
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="模型类型">
            <el-select v-model="modelForm.modelType">
              <el-option
                v-for="item in modelTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="modelForm.status">
              <el-option
                v-for="item in providerCreateStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="Max Tokens">
            <el-input-number v-model="modelForm.maxTokens" :min="1" :step="256" controls-position="right" />
          </el-form-item>
          <el-form-item label="默认温度">
            <el-input-number
              v-model="modelForm.temperatureDefault"
              :min="0"
              :max="2"
              :step="0.1"
              controls-position="right"
            />
          </el-form-item>
        </div>
        <div class="provider-tip">
          当前选择的提供方：<strong>{{ providerNameById(modelForm.providerId) }}</strong>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="modelDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="modelSubmitting" @click="handleCreateModel">
            确认创建
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.model-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.model-head,
.section-head,
.health-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.head-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.summary-card,
.provider-panel,
.model-panel {
  padding: 20px 22px;
}

.summary-card span {
  color: #9d7a58;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.summary-card strong {
  display: block;
  margin-top: 12px;
  font-family: "Noto Serif SC", serif;
  font-size: 30px;
}

.summary-card p,
.section-subtitle,
.health-message,
.provider-tip {
  margin: 12px 0 0;
  color: #6d5948;
}

.section-title {
  margin: 0;
  font-family: "Noto Serif SC", serif;
  font-size: 24px;
}

.section-subtitle {
  margin-top: 8px;
}

.filter-panel {
  padding: 18px 0 20px;
}

.filter-grid,
.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.filter-actions,
.dialog-footer,
.table-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.filter-actions,
.table-footer {
  margin-top: 18px;
}

.capability-list,
.health-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.health-result {
  margin-top: 18px;
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(255, 248, 238, 0.88);
}

.inline-error {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(168, 52, 26, 0.08);
  color: #8d4510;
}

.provider-tip {
  font-size: 13px;
}

@media (max-width: 960px) {
  .model-head,
  .section-head,
  .health-header {
    flex-direction: column;
  }

  .summary-strip,
  .filter-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .head-actions {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .head-actions,
  .filter-actions,
  .dialog-footer {
    flex-direction: column;
  }
}
</style>
