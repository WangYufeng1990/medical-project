import { JwtPayload } from '../types/common'

// Session-scoped credential storage (Review III C1-frontend): JWTs, refresh
// tokens and cached PHI no longer persist in localStorage — closing the tab
// ends the session and shrinks the XSS-exfiltration surface. Reads fall back
// to localStorage once so existing sessions survive the migration.
const STORAGE_KEYS = ['token', 'refreshToken', 'patientToken', 'patientRefreshToken',
  'userId', 'username', 'realName', 'patientInfo'] as const

export const tokenStore = {
  get: (k: string): string | null => sessionStorage.getItem(k) ?? localStorage.getItem(k),
  set: (k: string, v: string) => sessionStorage.setItem(k, v),
  remove: (k: string) => { sessionStorage.removeItem(k); localStorage.removeItem(k) },
  clearAll: () => STORAGE_KEYS.forEach(k => { sessionStorage.removeItem(k); localStorage.removeItem(k) }),
}

export function readPatientInfo(): { name?: string; patientId?: number; username?: string } {
  try {
    return JSON.parse(tokenStore.get('patientInfo') ?? '{}')
  } catch { return {} }
}

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
  const token = tokenStore.get('token')
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
