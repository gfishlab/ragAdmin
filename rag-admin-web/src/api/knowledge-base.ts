import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  CreateDocumentVersionRequest,
  CreateKnowledgeBaseDocumentRequest,
  DocumentChunk,
  DocumentDetail,
  DocumentVersion,
  KnowledgeBase,
  KnowledgeBaseDocument,
  KnowledgeBaseUpsertRequest,
  ModelDefinition,
  UploadUrlRequest,
  UploadUrlResponse,
} from '@/types/knowledge-base'
import { http, unwrapResponse } from './http'

export interface KnowledgeBaseListQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
}

export interface ModelListQuery {
  pageNo: number
  pageSize: number
  capabilityType?: string
  status?: string
}

export interface KnowledgeBaseDocumentListQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  parseStatus?: string
  enabled?: boolean
}

export async function listKnowledgeBases(
  query: KnowledgeBaseListQuery,
): Promise<PageResponse<KnowledgeBase>> {
  const response = await http.get<ApiResponse<PageResponse<KnowledgeBase>>>('/admin/knowledge-bases', {
    params: query,
  })
  return unwrapResponse(response.data)
}

export async function createKnowledgeBase(payload: KnowledgeBaseUpsertRequest): Promise<number | null> {
  const response = await http.post<ApiResponse<number | null>>('/admin/knowledge-bases', payload)
  return unwrapResponse(response.data)
}

export async function getKnowledgeBaseDetail(id: number): Promise<KnowledgeBase> {
  const response = await http.get<ApiResponse<KnowledgeBase>>(`/admin/knowledge-bases/${id}`)
  return unwrapResponse(response.data)
}

export async function updateKnowledgeBase(
  id: number,
  payload: KnowledgeBaseUpsertRequest,
): Promise<void> {
  const response = await http.put<ApiResponse<null>>(`/admin/knowledge-bases/${id}`, payload)
  unwrapResponse(response.data)
}

export async function listKnowledgeBaseDocuments(
  id: number,
  query: KnowledgeBaseDocumentListQuery,
): Promise<PageResponse<KnowledgeBaseDocument>> {
  const response = await http.get<ApiResponse<PageResponse<KnowledgeBaseDocument>>>(
    `/admin/knowledge-bases/${id}/documents`,
    {
      params: query,
    },
  )
  return unwrapResponse(response.data)
}

export async function getKnowledgeBaseDocumentUploadUrl(
  payload: UploadUrlRequest,
): Promise<UploadUrlResponse> {
  const response = await http.post<ApiResponse<UploadUrlResponse>>('/admin/files/upload-url', payload)
  return unwrapResponse(response.data)
}

export async function createKnowledgeBaseDocument(
  id: number,
  payload: CreateKnowledgeBaseDocumentRequest,
): Promise<number | null> {
  const response = await http.post<ApiResponse<number | null>>(`/admin/knowledge-bases/${id}/documents`, payload)
  return unwrapResponse(response.data)
}

export async function triggerDocumentParse(documentId: number): Promise<void> {
  const response = await http.post<ApiResponse<null>>(`/admin/documents/${documentId}/parse`)
  unwrapResponse(response.data)
}

export async function getDocumentDetail(documentId: number): Promise<DocumentDetail> {
  const response = await http.get<ApiResponse<DocumentDetail>>(`/admin/documents/${documentId}`)
  return unwrapResponse(response.data)
}

export async function listDocumentVersions(
  documentId: number,
  query: { pageNo: number; pageSize: number },
): Promise<PageResponse<DocumentVersion>> {
  const response = await http.get<ApiResponse<PageResponse<DocumentVersion>>>(
    `/admin/documents/${documentId}/versions`,
    {
      params: query,
    },
  )
  return unwrapResponse(response.data)
}

export async function listDocumentChunks(
  documentId: number,
  query: { pageNo: number; pageSize: number },
): Promise<PageResponse<DocumentChunk>> {
  const response = await http.get<ApiResponse<PageResponse<DocumentChunk>>>(
    `/admin/documents/${documentId}/chunks`,
    {
      params: query,
    },
  )
  return unwrapResponse(response.data)
}

export async function activateDocumentVersion(documentId: number, versionId: number): Promise<void> {
  const response = await http.put<ApiResponse<null>>(
    `/admin/documents/${documentId}/versions/${versionId}/activate`,
  )
  unwrapResponse(response.data)
}

export async function createDocumentVersion(
  documentId: number,
  payload: CreateDocumentVersionRequest,
): Promise<number | null> {
  const response = await http.post<ApiResponse<number | null>>(
    `/admin/documents/${documentId}/versions`,
    payload,
  )
  return unwrapResponse(response.data)
}

export async function listModels(query: ModelListQuery): Promise<PageResponse<ModelDefinition>> {
  const response = await http.get<ApiResponse<PageResponse<ModelDefinition>>>('/admin/models', {
    params: query,
  })
  return unwrapResponse(response.data)
}
