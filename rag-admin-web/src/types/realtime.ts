export interface TaskRealtimeEvent {
  eventType: string
  taskId?: number | null
  kbId?: number | null
  documentId?: number | null
  documentName?: string | null
  taskStatus?: string | null
  parseStatus?: string | null
  currentStepCode?: string | null
  currentStepName?: string | null
  progressPercent?: number | null
  message?: string | null
  terminal?: boolean | null
  occurredAt?: string | null
}
