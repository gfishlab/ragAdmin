import type { ApiResponse } from '@/types/api'
import type { VectorIndexOverview } from '@/types/statistics'
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
