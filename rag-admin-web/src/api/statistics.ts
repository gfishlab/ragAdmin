import type { ApiResponse } from '@/types/api'
import type { KnowledgeBaseChatStatistics, ModelCallStatistics, VectorIndexOverview } from '@/types/statistics'
import { http, unwrapResponse } from './http'

export interface VectorIndexListQuery {
  keyword?: string
  status?: string
}

export async function listVectorIndexes(
  query: VectorIndexListQuery = {},
): Promise<VectorIndexOverview[]> {
  const response = await http.get<ApiResponse<VectorIndexOverview[]>>('/admin/statistics/vector-indexes', {
    params: query,
  })
  return unwrapResponse(response.data)
}

export async function listModelCallStatistics(): Promise<ModelCallStatistics[]> {
  const response = await http.get<ApiResponse<ModelCallStatistics[]>>('/admin/statistics/model-calls')
  return unwrapResponse(response.data)
}

export async function getKnowledgeBaseChatStatistics(kbId: number): Promise<KnowledgeBaseChatStatistics> {
  const response = await http.get<ApiResponse<KnowledgeBaseChatStatistics>>(
    `/admin/statistics/knowledge-bases/${kbId}/chat`,
  )
  return unwrapResponse(response.data)
}
