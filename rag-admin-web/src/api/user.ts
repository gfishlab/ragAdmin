import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  AssignUserRolesRequest,
  CreateUserRequest,
  UpdateUserRequest,
  UserListItem,
  UserListQuery,
} from '@/types/user'
import { http, unwrapResponse } from './http'

export async function listUsers(query: UserListQuery): Promise<PageResponse<UserListItem>> {
  const response = await http.get<ApiResponse<PageResponse<UserListItem>>>('/admin/users', {
    params: query,
  })
  return unwrapResponse(response.data)
}

export async function createUser(payload: CreateUserRequest): Promise<UserListItem> {
  const response = await http.post<ApiResponse<UserListItem>>('/admin/users', payload)
  return unwrapResponse(response.data)
}

export async function updateUser(userId: number, payload: UpdateUserRequest): Promise<UserListItem> {
  const response = await http.put<ApiResponse<UserListItem>>(`/admin/users/${userId}`, payload)
  return unwrapResponse(response.data)
}

export async function assignUserRoles(userId: number, payload: AssignUserRolesRequest): Promise<void> {
  const response = await http.put<ApiResponse<null>>(`/admin/users/${userId}/roles`, payload)
  unwrapResponse(response.data)
}
