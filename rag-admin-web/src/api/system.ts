import type { ApiResponse } from '@/types/api'
import type { HealthCheckResponse } from '@/types/system'
import { http, unwrapResponse } from './http'

export async function getSystemHealth(): Promise<HealthCheckResponse> {
  const response = await http.get<ApiResponse<HealthCheckResponse>>('/admin/system/health')
  return unwrapResponse(response.data)
}
