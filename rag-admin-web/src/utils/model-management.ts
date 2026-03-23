import type { ModelCreateRequest, ModelDefinition, ModelProviderCreateRequest } from '@/types/model'

export type UiTagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

export type ModelScene = 'CHAT' | 'SYNC_TEXT' | 'ASYNC_TEXT' | 'VISION'

export const providerStatusOptions = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
]

export const providerCreateStatusOptions = providerStatusOptions.filter((item) => item.value)

export const capabilityOptions = [
  { label: '全部用途', value: '' },
  { label: '聊天', value: 'TEXT_GENERATION' },
  { label: '向量化', value: 'EMBEDDING' },
]

export const modelTypeOptions = [
  { label: '聊天', value: 'CHAT' },
  { label: '向量化', value: 'EMBEDDING' },
]

export function createEmptyModelForm(): ModelCreateRequest {
  return {
    providerId: null,
    modelCode: '',
    modelName: '',
    capabilityTypes: ['TEXT_GENERATION'],
    modelType: 'CHAT',
    maxTokens: null,
    temperatureDefault: 0.7,
    status: 'ENABLED',
  }
}

export function createEmptyProviderForm(): ModelProviderCreateRequest {
  return {
    providerCode: '',
    providerName: '',
    baseUrl: '',
    apiKeySecretRef: '',
    status: 'ENABLED',
  }
}

export function isEmbeddingModel(modelType: string): boolean {
  return modelType === 'EMBEDDING'
}

export function isChatModel(model: Pick<ModelDefinition, 'modelType' | 'capabilityTypes'>): boolean {
  return model.modelType === 'CHAT' || model.capabilityTypes.includes('TEXT_GENERATION')
}

export function allowedCapabilityTypes(modelType: string): string[] {
  return isEmbeddingModel(modelType) ? ['EMBEDDING'] : ['TEXT_GENERATION']
}

// 前端只暴露“模型用途”，实际提交给后端的 capabilityTypes 仍按用途自动派生。
export function normalizeCapabilityTypes(modelType: string, capabilityTypes: string[]): string[] {
  const allowed = allowedCapabilityTypes(modelType)
  const normalized = capabilityTypes.filter((item) => allowed.includes(item))
  return normalized.length > 0 ? normalized : allowed
}

export function normalizeRuntimeOptions(
  modelType: string,
  maxTokens: number | null | undefined,
  temperatureDefault: number | null | undefined,
): { maxTokens: number | null; temperatureDefault: number | null } {
  if (isEmbeddingModel(modelType)) {
    return {
      maxTokens: null,
      temperatureDefault: null,
    }
  }
  return {
    maxTokens: maxTokens || null,
    temperatureDefault: temperatureDefault ?? null,
  }
}

export function buildModelDraftFromDefinition(model: ModelDefinition): ModelCreateRequest {
  const runtimeOptions = normalizeRuntimeOptions(model.modelType, model.maxTokens, model.temperatureDefault)
  return {
    providerId: model.providerId,
    modelCode: model.modelCode,
    modelName: model.modelName,
    capabilityTypes: normalizeCapabilityTypes(model.modelType, model.capabilityTypes),
    modelType: model.modelType,
    maxTokens: runtimeOptions.maxTokens,
    temperatureDefault: runtimeOptions.temperatureDefault,
    status: model.status,
  }
}

export function sanitizeModelPayload(payload: ModelCreateRequest): ModelCreateRequest {
  const runtimeOptions = normalizeRuntimeOptions(
    payload.modelType,
    payload.maxTokens,
    payload.temperatureDefault,
  )
  return {
    ...payload,
    providerId: payload.providerId,
    modelCode: payload.modelCode.trim(),
    modelName: payload.modelName.trim(),
    capabilityTypes: normalizeCapabilityTypes(payload.modelType, payload.capabilityTypes),
    maxTokens: runtimeOptions.maxTokens,
    temperatureDefault: runtimeOptions.temperatureDefault,
  }
}

export function capabilityLabel(capability: string): string {
  if (capability === 'TEXT_GENERATION') {
    return '聊天'
  }
  if (capability === 'EMBEDDING') {
    return '向量化'
  }
  return capability
}

export function statusTagType(status: string): UiTagType {
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

export function runtimeOptionDisplay(modelType: string, value: number | string | null | undefined): number | string {
  if (isEmbeddingModel(modelType)) {
    return '不适用'
  }
  return value ?? '未配置'
}

export function detectModelScene(modelType: string, modelCode: string): ModelScene {
  if (!isEmbeddingModel(modelType)) {
    return 'CHAT'
  }
  const normalizedModelCode = modelCode.trim().toLowerCase()
  if (
    normalizedModelCode.includes('vl-embedding')
    || normalizedModelCode.includes('multimodal-embedding')
    || normalizedModelCode.includes('embedding-vision')
    || normalizedModelCode.includes('vision-plus')
  ) {
    return 'VISION'
  }
  if (normalizedModelCode.includes('text-embedding-async')) {
    return 'ASYNC_TEXT'
  }
  return 'SYNC_TEXT'
}

export function sceneLabel(scene: ModelScene): string {
  if (scene === 'CHAT') {
    return '聊天'
  }
  if (scene === 'SYNC_TEXT') {
    return '同步文本'
  }
  if (scene === 'ASYNC_TEXT') {
    return '异步文本'
  }
  return '视觉向量'
}

export function sceneTagType(scene: ModelScene): UiTagType {
  if (scene === 'CHAT') {
    return 'primary'
  }
  if (scene === 'SYNC_TEXT') {
    return 'success'
  }
  if (scene === 'ASYNC_TEXT') {
    return 'warning'
  }
  return 'danger'
}

export function sceneDescription(scene: ModelScene): string {
  if (scene === 'CHAT') {
    return '用于问答与回答生成，不参与文档切片向量化。'
  }
  if (scene === 'SYNC_TEXT') {
    return '当前可直接用于知识库文档切片向量化与检索。'
  }
  if (scene === 'ASYNC_TEXT') {
    return '当前仅支持登记与展示，尚未接入知识库运行链路。'
  }
  return '当前文本切片链路不支持视觉向量化输入。'
}

export function isArchitectureReady(scene: ModelScene): boolean {
  return scene === 'CHAT' || scene === 'SYNC_TEXT'
}

export function modelPurposeHint(modelType: string, modelCode: string): string {
  const scene = detectModelScene(modelType, modelCode)
  if (scene === 'CHAT') {
    return '用于问答和回答生成，不参与文档向量化。像 deepseek-v3.2 这类模型通常配置为聊天用途。'
  }
  if (scene === 'SYNC_TEXT') {
    return '会在文档解析完成后接收文本切片输入，生成向量并写入知识库索引，适用于当前架构。'
  }
  if (scene === 'ASYNC_TEXT') {
    return '会被识别为异步文本向量模型。当前平台允许登记，但还未接入批量文件提交、任务轮询和结果回写链路。'
  }
  return '会被识别为视觉向量模型，这类模型通常要求图片、URL 或多模态输入，当前文本切片链路不支持。'
}
