import type { ApiResponse, PageResponse } from '@/types/api'
import type { PromptTemplateListQuery, PromptTemplateRecord, PromptTemplateUpdateRequest } from '@/types/prompt-template'
import { http, unwrapResponse } from './http'

export async function listPromptTemplates(
  query: PromptTemplateListQuery,
): Promise<PageResponse<PromptTemplateRecord>> {
  const response = await http.get<ApiResponse<PageResponse<PromptTemplateRecord>>>(
    '/admin/prompt-templates',
    {
      params: {
        templateCode: query.templateCode || undefined,
        capabilityType: query.capabilityType || undefined,
        status: query.status || undefined,
        pageNo: query.pageNo ?? 1,
        pageSize: query.pageSize ?? 20,
      },
    },
  )
  return unwrapResponse(response.data)
}

export async function getPromptTemplate(id: number): Promise<PromptTemplateRecord> {
  const response = await http.get<ApiResponse<PromptTemplateRecord>>(
    `/admin/prompt-templates/${id}`,
  )
  return unwrapResponse(response.data)
}

export async function updatePromptTemplate(
  id: number,
  body: PromptTemplateUpdateRequest,
): Promise<PromptTemplateRecord> {
  const response = await http.put<ApiResponse<PromptTemplateRecord>>(
    `/admin/prompt-templates/${id}`,
    body,
  )
  return unwrapResponse(response.data)
}
