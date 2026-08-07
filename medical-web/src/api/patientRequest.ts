import axios, { AxiosRequestConfig, AxiosResponse, AxiosError, InternalAxiosRequestConfig } from 'axios'
import { scheduleDelayMs } from '../utils/auth'
import { Result } from '../types/common'
import { LoginResponse } from '../types/entities'

const patientRequest = axios.create({ baseURL: '/api/v1', timeout: 15000 })

patientRequest.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('patientToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let isRefreshing = false
let refreshSubscribers: ((token: string) => void)[] = []

function subscribeTokenRefresh(cb: (token: string) => void) {
  refreshSubscribers.push(cb)
}

function onRefreshed(token: string) {
  refreshSubscribers.forEach(cb => cb(token))
  refreshSubscribers = []
}

// The interceptor unwraps the Result<T> envelope at runtime, so the payload
// (not an AxiosResponse) flows to callers. Axios's type signature requires
// AxiosResponse here; the `http` facade below casts the actual payload type.
function asAxiosResponse(p: unknown): AxiosResponse {
  return p as unknown as AxiosResponse
}

patientRequest.interceptors.response.use(
  (res: AxiosResponse<Result<unknown>>) => {
    if (res.data?.code === 200) return asAxiosResponse(res.data.data)
    return Promise.reject(new Error(res.data?.message || 'Request failed'))
  },
  async (err: AxiosError<{ message?: string }>) => {
    const originalRequest = err.config
    if (!originalRequest) return Promise.reject(err)
    if (err.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true
      const refreshToken = localStorage.getItem('patientRefreshToken')
      if (refreshToken) {
        if (!isRefreshing) {
          isRefreshing = true
          try {
            const res = await axios.post<Result<LoginResponse>>('/api/v1/patient/refresh', { refreshToken })
            const newToken = res.data.data?.token || ''
            localStorage.setItem('patientToken', newToken)
            if (res.data.data?.refreshToken) {
              localStorage.setItem('patientRefreshToken', res.data.data.refreshToken)
            }
            scheduleProactiveRefresh()
            onRefreshed(newToken)
            isRefreshing = false
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return patientRequest(originalRequest)
          } catch {
            isRefreshing = false
            refreshSubscribers = []
            localStorage.removeItem('patientToken')
            localStorage.removeItem('patientRefreshToken')
            if (!originalRequest.silent) window.location.href = '/patient/login'
            return Promise.reject(err)
          }
        } else {
          return new Promise<AxiosResponse>(resolve => {
            subscribeTokenRefresh((token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(patientRequest(originalRequest))
            })
          })
        }
      }
      localStorage.removeItem('patientToken')
      if (!originalRequest.silent) window.location.href = '/patient/login'
    }
    if (err.response?.status === 429) {
      const retryAfter = err.response.headers['retry-after']
      const msg = retryAfter ? `Rate limited. Try again in ${retryAfter}s.` : 'Too many requests. Please wait.'
      return Promise.reject(new Error(msg))
    }
    if (err.response?.data?.message) return Promise.reject(new Error(err.response.data.message))
    return Promise.reject(err)
  }
)

let proactiveTimer: ReturnType<typeof setTimeout> | null = null

// Refresh the patient access token at 80% of its TTL so expiry never surfaces
// as a visible 401. Tokens are re-read at fire time to survive rotation.
export function scheduleProactiveRefresh() {
  if (proactiveTimer) clearTimeout(proactiveTimer)
  const token = localStorage.getItem('patientToken')
  if (!token) return
  proactiveTimer = setTimeout(async () => {
    const refreshToken = localStorage.getItem('patientRefreshToken')
    if (refreshToken) {
      try {
        const res = await axios.post<Result<LoginResponse>>('/api/v1/patient/refresh', { refreshToken })
        const newToken = res.data.data?.token || ''
        localStorage.setItem('patientToken', newToken)
        if (res.data.data?.refreshToken) {
          localStorage.setItem('patientRefreshToken', res.data.data.refreshToken)
        }
      } catch { /* leave tokens; the 401 chain handles stale sessions */ }
    }
    scheduleProactiveRefresh()
  }, scheduleDelayMs(token))
}

// Typed facade: runtime unwraps Result<T>, so each verb resolves to the
// payload — mirror that in the static type instead of AxiosResponse<T>.
export const http = {
  get: <T>(url: string, config?: AxiosRequestConfig) => patientRequest.get(url, config) as Promise<T>,
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => patientRequest.post(url, data, config) as Promise<T>,
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => patientRequest.put(url, data, config) as Promise<T>,
  delete: <T>(url: string, config?: AxiosRequestConfig) => patientRequest.delete(url, config) as Promise<T>,
}

export default patientRequest
