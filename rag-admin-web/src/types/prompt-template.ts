import type { PageResponse } from './api'

export interface PromptTemplateRecord {
  id: number
  templateCode: string
  templateName: string
  capabilityType: string | null
  promptContent: string
  versionNo: number
  status: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface PromptTemplateListQuery {
  templateCode?: string
  capabilityType?: string
  status?: string
  pageNo?: number
  pageSize?: number
}

export interface PromptTemplateUpdateRequest {
  templateName: string
  promptContent: string
  description?: string
  status?: string
}

export type PromptTemplatePage = PageResponse<PromptTemplateRecord>
