import type { CurrentUser } from '@/types/auth'

export function hasPermission(user: CurrentUser | null | undefined, code: string): boolean {
  return user?.permissions?.includes(code) ?? false
}

export function hasAnyPermission(user: CurrentUser | null | undefined, codes: string[]): boolean {
  if (!codes.length) {
    return true
  }
  return codes.some((code) => hasPermission(user, code))
}
