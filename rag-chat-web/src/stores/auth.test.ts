import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'

let accessTokenValue = ''
let refreshTokenValue = ''
let currentUserRawValue = ''

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getCurrentUser: vi.fn(),
}))

vi.mock('@/utils/token-storage', () => ({
  getAccessToken: vi.fn(() => accessTokenValue),
  getRefreshToken: vi.fn(() => refreshTokenValue),
  getCurrentUserRaw: vi.fn(() => currentUserRawValue),
  setAccessToken: vi.fn((token: string) => {
    accessTokenValue = token
  }),
  setRefreshToken: vi.fn((token: string) => {
    refreshTokenValue = token
  }),
  setCurrentUserRaw: vi.fn((value: string) => {
    currentUserRawValue = value
  }),
  clearSessionStorage: vi.fn(() => {
    accessTokenValue = ''
    refreshTokenValue = ''
    currentUserRawValue = ''
  }),
}))

describe('useAuthStore', () => {
  beforeEach(() => {
    accessTokenValue = ''
    refreshTokenValue = ''
    currentUserRawValue = ''
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('存在本地缓存用户时也应刷新服务端当前用户', async () => {
    accessTokenValue = 'access-token'
    currentUserRawValue = JSON.stringify({
      id: 2,
      username: 'app-user',
      displayName: '问答前台用户',
      mobile: '13800000000',
      roles: ['APP_USER'],
      webSearchAvailable: false,
    })
    vi.mocked(authApi.getCurrentUser).mockResolvedValue({
      id: 2,
      username: 'app-user',
      displayName: '问答前台用户',
      mobile: '13800000000',
      roles: ['APP_USER'],
      webSearchAvailable: true,
    })

    const store = useAuthStore()

    expect(store.currentUser?.webSearchAvailable).toBe(false)

    await store.hydrateCurrentUser()

    expect(authApi.getCurrentUser).toHaveBeenCalledTimes(1)
    expect(store.currentUser?.webSearchAvailable).toBe(true)
    expect(store.bootstrapFinished).toBe(true)
    expect(JSON.parse(currentUserRawValue).webSearchAvailable).toBe(true)
  })

  it('未登录时不应请求当前用户接口', async () => {
    const store = useAuthStore()

    await store.hydrateCurrentUser()

    expect(authApi.getCurrentUser).not.toHaveBeenCalled()
    expect(store.bootstrapFinished).toBe(true)
  })
})
