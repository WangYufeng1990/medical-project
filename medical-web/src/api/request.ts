import { createApiClient } from './createClient'

const client = createApiClient({
  tokenKey: 'token',
  refreshTokenKey: 'refreshToken',
  refreshUrl: '/api/v1/auth/refresh',
  loginPath: '/login',
})

export const http = client.http
export const scheduleProactiveRefresh = client.scheduleProactiveRefresh
export const request = client.instance
export type { BlobDownload } from './createClient'
export default client.instance
