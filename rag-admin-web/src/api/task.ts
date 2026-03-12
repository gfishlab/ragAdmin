import type { ApiResponse, PageResponse } from '@/types/api'
import type { TaskRecord } from '@/types/task'
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
