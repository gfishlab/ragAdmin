export interface UserListItem {
  id: number
  username: string
  displayName: string
  email?: string | null
  mobile?: string | null
  status: string
  roles: string[]
}

export interface UserListQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
}

export interface CreateUserRequest {
  username: string
  password: string
  displayName: string
  email?: string | null
  mobile?: string | null
  status: string
  roleCodes: string[]
}

export interface UpdateUserRequest {
  displayName: string
  email?: string | null
  mobile?: string | null
  status: string
  password?: string | null
}

export interface AssignUserRolesRequest {
  roleCodes: string[]
}
