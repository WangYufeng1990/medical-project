import { JwtPayload } from '../types/common'

export function parseJwt(token: string): JwtPayload {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const json = atob(base64)
    return JSON.parse(json)
  } catch { console.warn('Failed to parse JWT token'); return {} }
}

// Milliseconds until 80% of the token's lifetime has elapsed — when a
// proactive refresh should fire so expiry never surfaces as a visible failure.
export function scheduleDelayMs(token: string): number {
  const payload = parseJwt(token)
  if (!payload.exp) return 0
  const expMs = payload.exp * 1000
  if (payload.iat) {
    const ttlMs = expMs - payload.iat * 1000
    return Math.max(0, expMs - Date.now() - ttlMs * 0.2)
  }
  return Math.max(0, expMs - Date.now() - 60_000)
}

export function getUserRoles(): string[] {
  const token = localStorage.getItem('token')
  if (!token) return []
  const payload = parseJwt(token)
  const authorities: string[] = payload.groups || payload.roles || []
  return authorities.map((r: string) => r.replace('ROLE_', ''))
}

export function hasRole(role: string): boolean {
  return getUserRoles().includes(role)
}

export function hasAnyRole(roles: string[]): boolean {
  const userRoles = getUserRoles()
  return roles.some(r => userRoles.includes(r))
}
