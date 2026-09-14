import { createApiClient } from './createClient'

const client = createApiClient({
  tokenKey: 'patientToken',
  refreshTokenKey: 'patientRefreshToken',
  refreshUrl: '/api/v1/patient/refresh',
  loginPath: '/patient/login',
})

export const http = client.http
export const scheduleProactiveRefresh = client.scheduleProactiveRefresh
export default client.instance
