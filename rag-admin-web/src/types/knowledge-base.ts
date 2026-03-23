import type { ModelDefinition } from './model'

export type { ModelDefinition }

export interface KnowledgeBase {
  id: number
  kbCode: string
  kbName: string
  description: string | null
  embeddingModelId: number | null
  embeddingModelName: string | null
  retrieveTopK: number
  rerankEnabled: boolean
  status: string
}

export interface KnowledgeBaseUpsertRequest {
  kbCode: string
  kbName: string
  description: string | null
  embeddingModelId: number | null
  retrieveTopK: number
  rerankEnabled: boolean
  status: string
}

export interface KnowledgeBaseDocument {
  documentId: number
  docName: string
  docType: string
  parseStatus: string
  enabled: boolean
  kbId?: number | null
  kbName?: string | null
  storageBucket?: string | null
  storageObjectKey?: string | null
  currentVersion?: number | null
  fileSize?: number | null
  contentHash?: string | null
  createdAt: string
  updatedAt?: string | null
}

export interface UploadUrlRequest {
  fileName: string
  contentType: string
  bizType: 'KB_DOCUMENT'
}

export interface UploadUrlResponse {
  bucket: string
  objectKey: string
  uploadUrl: string
}

export interface DocumentUploadCapability {
  ocrEnabled: boolean
  ocrAvailable: boolean
  ocrMessage: string
  ocrLanguage: string
  ocrMaxPdfPages: number
  supportedDocTypes: string[]
  ocrImageDocTypes: string[]
}

export interface CreateKnowledgeBaseDocumentRequest {
  docName: string
  docType: string
  storageBucket: string
  storageObjectKey: string
  fileSize?: number | null
  contentHash?: string | null
}

export interface DocumentDetail {
  documentId: number
  kbId: number | null
  kbName?: string | null
  docName: string
  docType: string
  currentVersion?: number | null
  parseStatus: string
  enabled: boolean
  storageBucket?: string | null
  storageObjectKey?: string | null
  fileSize?: number | null
  contentHash?: string | null
  createdAt: string
  updatedAt?: string | null
}

export interface DocumentVersion {
  versionId: number
  storageObjectKey: string
  contentHash?: string | null
  active?: boolean
  createdAt: string
}

export interface CreateDocumentVersionRequest {
  storageBucket: string
  storageObjectKey: string
  contentHash?: string | null
  fileSize?: number | null
}

export interface DocumentChunk {
  chunkId: number
  chunkNo?: number | null
  content?: string | null
  contentSnippet?: string | null
  score?: number | null
  tokenCount?: number | null
  charCount?: number | null
}
