import type { ApiResponse, PageResponse } from '@/types/api'
import type {
  AssignUserRolesRequest,
  CreateUserRequest,
  KickoutUserSessionRequest,
  UpdateUserRequest,
  UserSessionDetail,
  UserSessionListItem,
  UserSessionListQuery,
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

export async function listUserSessions(query: UserSessionListQuery): Promise<PageResponse<UserSessionListItem>> {
  const response = await http.get<ApiResponse<PageResponse<UserSessionListItem>>>('/admin/user-sessions', {
    params: query,
  })
  return unwrapResponse(response.data)
}

export async function getUserSessionDetail(userId: number): Promise<UserSessionDetail> {
  const response = await http.get<ApiResponse<UserSessionDetail>>(`/admin/user-sessions/${userId}`)
  return unwrapResponse(response.data)
}

export async function kickoutUserSession(userId: number, payload: KickoutUserSessionRequest): Promise<void> {
  const response = await http.post<ApiResponse<null>>(`/admin/user-sessions/${userId}/kickout`, payload)
  unwrapResponse(response.data)
}
