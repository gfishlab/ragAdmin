export interface TaskRecord {
  taskId: number
  taskType: string
  taskStatus: string
  bizId: number | string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}
