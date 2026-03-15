export interface TaskRecord {
  taskId: number
  taskType: string
  taskStatus: string
  bizType?: string | null
  bizId: number | string | null
  kbId?: number | null
  documentId?: number | null
  documentName?: string | null
  retryCount?: number | null
  errorMessage: string | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface TaskDetail extends TaskRecord {
  documentVersionId?: number | null
  documentParseStatus?: string | null
  steps?: Array<{
    stepCode: string
    stepName: string
    stepStatus: string
    errorMessage?: string | null
    startedAt?: string | null
    finishedAt?: string | null
  }>
  retryRecords?: Array<{
    retryNo: number
    retryReason: string
    retryResult: string
    createdAt: string
  }>
}

export interface TaskSummary {
  total: number
  waiting: number
  running: number
  success: number
  failed: number
  canceled: number
}
