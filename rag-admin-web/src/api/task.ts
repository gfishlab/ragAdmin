import type { ApiResponse, PageResponse } from '@/types/api'
import type { TaskDetail, TaskRecord, TaskSummary } from '@/types/task'
import { http, unwrapResponse } from './http'

export interface TaskListQuery {
  pageNo: number
  pageSize: number
  taskType?: string
  taskStatus?: string
  bizId?: string
}

export async function listTasks(query: TaskListQuery): Promise<PageResponse<TaskRecord>> {
  const response = await http.get<ApiResponse<PageResponse<TaskRecord>>>('/admin/tasks', {
    params: query,
  })
  return unwrapResponse(response.data)
}

export async function retryTask(taskId: number): Promise<void> {
  const response = await http.post<ApiResponse<null>>(`/admin/tasks/${taskId}/retry`)
  unwrapResponse(response.data)
}

export async function getTaskDetail(taskId: number): Promise<TaskDetail> {
  const response = await http.get<ApiResponse<TaskDetail>>(`/admin/tasks/${taskId}`)
  return unwrapResponse(response.data)
}

export async function getTaskSummary(query: {
  taskType?: string
  bizId?: string
}): Promise<TaskSummary> {
  const response = await http.get<ApiResponse<TaskSummary>>('/admin/tasks/summary', {
    params: query,
  })
  return unwrapResponse(response.data)
}
