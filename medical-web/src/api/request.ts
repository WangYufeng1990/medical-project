import axios from 'axios'
import { scheduleDelayMs } from '../utils/auth'

const request = axios.create({ baseURL: '/api/v1', timeout: 15000 })

request.interceptors.request.use((config: any) => {
  const token = localStorage.getItem('token')
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

request.interceptors.response.use(
  async (res: any) => {
    if (res.config.responseType === 'blob') {
      if (res.status < 400) return res.data
      const text = await res.data.text()
      try {
        const json = JSON.parse(text)
        return Promise.reject(new Error(json.message || 'Export failed'))
      } catch {
        return Promise.reject(new Error('Export failed'))
      }
    }
    return res.data.code === 200 ? res.data.data : Promise.reject(new Error(res.data.message))
  },
  async (err: any) => {
    const originalRequest = err.config
    if (err.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true
      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) {
        if (!isRefreshing) {
          isRefreshing = true
          try {
            const res = await axios.post('/api/v1/auth/refresh', { refreshToken })
            const newToken = res.data.data.accessToken || res.data.data.token
            localStorage.setItem('token', newToken)
            if (res.data.data.refreshToken) {
              localStorage.setItem('refreshToken', res.data.data.refreshToken)
            }
            scheduleProactiveRefresh()
            onRefreshed(newToken)
            isRefreshing = false
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return request(originalRequest)
          } catch {
            isRefreshing = false
            refreshSubscribers = []
            localStorage.removeItem('token')
            localStorage.removeItem('refreshToken')
            if (!originalRequest.silent) window.location.href = '/login'
            return Promise.reject(err)
          }
        } else {
          return new Promise(resolve => {
            subscribeTokenRefresh((token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(request(originalRequest))
            })
          })
        }
      }
      localStorage.removeItem('token')
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
  const token = localStorage.getItem('token')
  if (!token) return
  proactiveTimer = setTimeout(async () => {
    const refreshToken = localStorage.getItem('refreshToken')
    if (refreshToken) {
      try {
        const res = await axios.post('/api/v1/auth/refresh', { refreshToken })
        const newToken = res.data.data.accessToken || res.data.data.token
        localStorage.setItem('token', newToken)
        if (res.data.data.refreshToken) {
          localStorage.setItem('refreshToken', res.data.data.refreshToken)
        }
      } catch { /* leave tokens; the 401 chain handles stale sessions */ }
    }
    scheduleProactiveRefresh()
  }, scheduleDelayMs(token))
}

export default request
