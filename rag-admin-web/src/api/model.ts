import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  BatchDeleteModelsRequest,
  ModelCreateRequest,
  ModelBatchDeleteResult,
  ModelDefinition,
  ModelHealthCheck,
  ModelProvider,
  ModelProviderCreateRequest,
  ModelProviderHealthCheck,
  UpdateModelRequest,
} from '@/types/model'
import { http, unwrapResponse } from './http'

// 本地模型首次冷启动、拉起权重或 CPU 推理都可能显著慢于普通后台接口，
// 因此探活请求单独放宽超时，避免被全局 15 秒超时提前打断。
const MODEL_HEALTH_CHECK_TIMEOUT_MS = 240_000

export interface ModelListQuery {
  pageNo: number
  pageSize: number
  providerCode?: string
  capabilityType?: string
  status?: string
}

export async function listModelProviders(): Promise<ModelProvider[]> {
  const response = await http.get<ApiResponse<ModelProvider[]>>('/admin/model-providers')
  return unwrapResponse(response.data)
}

export async function createModelProvider(payload: ModelProviderCreateRequest): Promise<ModelProvider> {
  const response = await http.post<ApiResponse<ModelProvider>>('/admin/model-providers', payload)
  return unwrapResponse(response.data)
}

export async function healthCheckModelProvider(providerId: number): Promise<ModelProviderHealthCheck> {
  const response = await http.post<ApiResponse<ModelProviderHealthCheck>>(
    `/admin/model-providers/${providerId}/health-check`,
    undefined,
    { timeout: MODEL_HEALTH_CHECK_TIMEOUT_MS },
  )
  return unwrapResponse(response.data)
}

export async function listModels(query: ModelListQuery): Promise<PageResponse<ModelDefinition>> {
  const response = await http.get<ApiResponse<PageResponse<ModelDefinition>>>('/admin/models', {
    params: query,
  })
  return unwrapResponse(response.data)
}

export async function createModel(payload: ModelCreateRequest): Promise<ModelDefinition> {
  const response = await http.post<ApiResponse<ModelDefinition>>('/admin/models', payload)
  return unwrapResponse(response.data)
}

export async function updateModel(modelId: number, payload: UpdateModelRequest): Promise<ModelDefinition> {
  const response = await http.put<ApiResponse<ModelDefinition>>(`/admin/models/${modelId}`, payload)
  return unwrapResponse(response.data)
}

export async function setDefaultChatModel(modelId: number): Promise<ModelDefinition> {
  const response = await http.post<ApiResponse<ModelDefinition>>(`/admin/models/${modelId}/default-chat-model`)
  return unwrapResponse(response.data)
}

export async function deleteModel(modelId: number): Promise<void> {
  const response = await http.delete<ApiResponse<null>>(`/admin/models/${modelId}`)
  unwrapResponse(response.data)
}

export async function batchDeleteModels(payload: BatchDeleteModelsRequest): Promise<ModelBatchDeleteResult> {
  const response = await http.post<ApiResponse<ModelBatchDeleteResult>>('/admin/models/batch-delete', payload)
  return unwrapResponse(response.data)
}

export async function healthCheckModel(modelId: number): Promise<ModelHealthCheck> {
  const response = await http.post<ApiResponse<ModelHealthCheck>>(
    `/admin/models/${modelId}/health-check`,
    undefined,
    { timeout: MODEL_HEALTH_CHECK_TIMEOUT_MS },
  )
  return unwrapResponse(response.data)
}
