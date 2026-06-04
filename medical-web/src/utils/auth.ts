export function parseJwt(token: string): any {
  try {
    const base64Url = token.split('.')[1]
    return JSON.parse(atob(base64Url))
  } catch { return {} }
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
