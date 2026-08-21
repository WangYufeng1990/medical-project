import axios, { AxiosRequestConfig, AxiosResponse, AxiosError, InternalAxiosRequestConfig } from 'axios'
import { scheduleDelayMs, tokenStore } from '../utils/auth'
import { Result } from '../types/common'
import { LoginResponse } from '../types/entities'

// Custom flags read by the 401/429 interceptors below.
declare module 'axios' {
  interface AxiosRequestConfig {
    _retry?: boolean
    silent?: boolean
  }
}

const request = axios.create({ baseURL: '/api/v1', timeout: 15000 })

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.get('token')
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

request.interceptors.response.use(
  async (res: AxiosResponse<Result<unknown> | Blob>) => {
    if (res.config.responseType === 'blob') {
      if (res.status < 400) return asAxiosResponse(res.data)
      const text = await (res.data as Blob).text()
      try {
        const json = JSON.parse(text)
        return Promise.reject(new Error(json.message || 'Export failed'))
      } catch {
        return Promise.reject(new Error('Export failed'))
      }
    }
    const result = res.data as Result<unknown>
    return asAxiosResponse(result.code === 200 ? result.data : Promise.reject(new Error(result.message)))
  },
  async (err: AxiosError<{ message?: string }>) => {
    const originalRequest = err.config
    if (!originalRequest) return Promise.reject(err)
    if (err.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true
      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) {
        if (!isRefreshing) {
          isRefreshing = true
          try {
            const res = await axios.post<Result<LoginResponse>>('/api/v1/auth/refresh', { refreshToken })
            const newToken = res.data.data?.accessToken || res.data.data?.token || ''
            tokenStore.set('token', newToken)
            if (res.data.data?.refreshToken) {
              tokenStore.set('refreshToken', res.data.data.refreshToken)
            }
            scheduleProactiveRefresh()
            onRefreshed(newToken)
            isRefreshing = false
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return request(originalRequest)
          } catch {
            isRefreshing = false
            refreshSubscribers = []
            tokenStore.remove('token')
            tokenStore.remove('refreshToken')
            if (!originalRequest.silent) window.location.href = '/login'
            return Promise.reject(err)
          }
        } else {
          return new Promise<AxiosResponse>(resolve => {
            subscribeTokenRefresh((token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(request(originalRequest))
            })
          })
        }
      }
      tokenStore.remove('token')
      if (!originalRequest.silent) window.location.href = '/login'
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

// Refresh the access token at 80% of its TTL so expiry never surfaces as a
// visible 401. Tokens are re-read at fire time to survive rotation.
export function scheduleProactiveRefresh() {
  if (proactiveTimer) clearTimeout(proactiveTimer)
  const token = tokenStore.get('token')
  if (!token) return
  proactiveTimer = setTimeout(async () => {
    const refreshToken = localStorage.getItem('refreshToken')
    if (refreshToken) {
      try {
        const res = await axios.post<Result<LoginResponse>>('/api/v1/auth/refresh', { refreshToken })
        const newToken = res.data.data?.accessToken || res.data.data?.token || ''
        tokenStore.set('token', newToken)
        if (res.data.data?.refreshToken) {
          tokenStore.set('refreshToken', res.data.data.refreshToken)
        }
      } catch { /* leave tokens; the 401 chain handles stale sessions */ }
    }
    scheduleProactiveRefresh()
  }, scheduleDelayMs(token))
}

// Typed facade: runtime unwraps Result<T>, so each verb resolves to the
// payload — mirror that in the static type instead of AxiosResponse<T>.
export const http = {
  get: <T>(url: string, config?: AxiosRequestConfig) => request.get(url, config) as Promise<T>,
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => request.post(url, data, config) as Promise<T>,
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => request.put(url, data, config) as Promise<T>,
  delete: <T>(url: string, config?: AxiosRequestConfig) => request.delete(url, config) as Promise<T>,
}

export default request
