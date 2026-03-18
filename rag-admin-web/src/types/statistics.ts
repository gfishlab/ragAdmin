export interface VectorIndexOverview {
  kbId: number
  kbCode: string
  kbName: string
  kbStatus: string
  configuredEmbeddingModelId?: number | null
  effectiveEmbeddingModelId?: number | null
  embeddingModelSource?: string | null
  embeddingModelCode?: string | null
  embeddingModelName?: string | null
  documentCount: number
  successDocumentCount: number
  chunkCount: number
  vectorRefCount: number
  collectionName?: string | null
  embeddingDim?: number | null
  latestVectorizedAt?: string | null
  milvusStatus: string
  milvusMessage: string
}
