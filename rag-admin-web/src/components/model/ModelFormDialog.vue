<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { ModelCreateRequest, ModelProvider } from '@/types/model'
import {
  createEmptyModelForm,
  detectModelScene,
  modelPurposeHint,
  modelTypeOptions,
  providerCreateStatusOptions,
  runtimeOptionDisplay,
  sceneLabel,
  sceneTagType,
  isEmbeddingModel,
  isRerankerModel,
} from '@/utils/model-management'

const props = defineProps<{
  mode: 'create' | 'edit'
  submitting: boolean
  providers: ModelProvider[]
  initialForm: ModelCreateRequest
}>()

const visible = defineModel<boolean>({ required: true })

const emit = defineEmits<{
  submit: [payload: ModelCreateRequest]
}>()

const modelForm = reactive<ModelCreateRequest>(createEmptyModelForm())

const dialogTitle = computed(() => (props.mode === 'create' ? '新增模型定义' : '编辑模型定义'))
const dialogConfirmText = computed(() => (props.mode === 'create' ? '确认创建' : '确认保存'))
const showGenerationOptionFields = computed(() =>
  !isEmbeddingModel(modelForm.modelType) && !isRerankerModel(modelForm.modelType)
)
const currentScene = computed(() => detectModelScene(modelForm.modelType, modelForm.modelCode))
const currentPurposeHint = computed(() => modelPurposeHint(modelForm.modelType, modelForm.modelCode))

function syncForm(): void {
  Object.assign(modelForm, createEmptyModelForm(), props.initialForm)
}

function providerNameById(providerId: number | null): string {
  if (!providerId) {
    return '未绑定'
  }
  return props.providers.find((item) => item.id === providerId)?.providerName ?? `#${providerId}`
}

function handleSubmit(): void {
  emit('submit', {
    ...modelForm,
    modelCode: modelForm.modelCode.trim(),
    modelName: modelForm.modelName.trim(),
  })
}

watch(
  () => visible.value,
  (isVisible) => {
    if (isVisible) {
      syncForm()
    }
  },
  { immediate: true },
)

watch(
  () => props.initialForm,
  () => {
    if (visible.value) {
      syncForm()
    }
  },
  { deep: true },
)

watch(
  () => modelForm.modelType,
  (modelType) => {
    if (isEmbeddingModel(modelType) || isRerankerModel(modelType)) {
      modelForm.maxTokens = null
      modelForm.temperatureDefault = null
      return
    }
    if (modelForm.temperatureDefault == null) {
      modelForm.temperatureDefault = 0.7
    }
  },
)
</script>

<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="680px" @closed="syncForm">
    <section class="mode-preview">
      <div>
        <small>模型识别结果</small>
        <strong>{{ sceneLabel(currentScene) }}</strong>
      </div>
      <el-tag :type="sceneTagType(currentScene)" effect="dark">
        {{ sceneLabel(currentScene) }}
      </el-tag>
    </section>

    <el-alert
      v-if="modelForm.modelType === 'EMBEDDING' || modelForm.modelType === 'RERANKER'"
      :title="currentPurposeHint"
      :type="currentScene === 'SYNC_TEXT' ? 'success' : currentScene === 'ASYNC_TEXT' ? 'warning' : 'error'"
      :closable="false"
      show-icon
      class="mode-alert"
    />

    <el-form label-position="top" class="model-form">
      <div class="form-grid">
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
        <el-form-item label="模型编码">
          <el-input v-model="modelForm.modelCode" placeholder="例如 qwen-max / text-embedding-v3 / deepseek-v3.2" />
        </el-form-item>

        <el-form-item label="模型名称">
          <el-input v-model="modelForm.modelName" placeholder="请输入模型展示名称" />
        </el-form-item>
      </div>

      <div class="form-grid">
        <el-form-item label="模型用途">
          <el-select v-model="modelForm.modelType">
            <el-option
              v-for="item in modelTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <article class="provider-card">
          <small>当前选择的提供方</small>
          <strong>{{ providerNameById(modelForm.providerId) }}</strong>
          <span>保存时会根据提供方和模型编码做能力校验。</span>
        </article>
      </div>

      <div v-if="showGenerationOptionFields" class="form-grid">
        <el-form-item label="最大令牌数">
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

      <article v-else class="runtime-preview">
        <small>运行参数</small>
        <span>最大令牌数：{{ runtimeOptionDisplay(modelForm.modelType, modelForm.maxTokens) }}</span>
        <span>默认温度：{{ runtimeOptionDisplay(modelForm.modelType, modelForm.temperatureDefault) }}</span>
      </article>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ dialogConfirmText }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.mode-preview {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(255, 248, 240, 0.88);
  border: 1px solid rgba(198, 107, 34, 0.14);
}

.mode-preview small,
.provider-card small,
.runtime-preview small {
  display: block;
  color: #8c735f;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.mode-preview strong {
  display: block;
  margin-top: 8px;
  font-size: 20px;
}

.mode-alert {
  margin-top: 14px;
}

.model-form {
  margin-top: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.provider-card,
.runtime-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 100%;
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(250, 247, 242, 0.94);
  border: 1px solid rgba(141, 69, 16, 0.1);
}

.provider-card strong {
  font-size: 18px;
}

.provider-card span,
.runtime-preview span {
  color: #67564a;
  line-height: 1.6;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 720px) {
  .form-grid,
  .mode-preview {
    grid-template-columns: 1fr;
  }

  .mode-preview {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
