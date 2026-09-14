import axios, {
  AxiosError,
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios'
import { scheduleDelayMs, tokenStore } from '../utils/auth'
import { Result } from '../types/common'

declare module 'axios' {
  interface AxiosRequestConfig {
    _retry?: boolean
    silent?: boolean
  }
}

/** A download resolves to the Blob plus the filename the server chose. */
export interface BlobDownload {
  blob: Blob
  filename: string | null
}

export interface ApiClientOptions {
  /** Token storage key for the access token. */
  tokenKey: string
  /** Token storage key for the refresh token. */
  refreshTokenKey: string
  /** Refresh endpoint, called with bare axios so it cannot recurse into these interceptors. */
  refreshUrl: string
  /** Where to send the browser once the session cannot be recovered. */
  loginPath: string
}

export interface ApiClient {
  instance: AxiosInstance
  http: {
    get: <T>(url: string, config?: AxiosRequestConfig) => Promise<T>
    post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => Promise<T>
    put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => Promise<T>
    delete: <T>(url: string, config?: AxiosRequestConfig) => Promise<T>
  }
  scheduleProactiveRefresh: () => void
}

// The interceptor unwraps the Result<T> envelope at runtime, so the payload
// (not an AxiosResponse) flows to callers. Axios's type signature requires
// AxiosResponse here; the `http` facade casts the actual payload type.
function asAxiosResponse(p: unknown): AxiosResponse {
  return p as unknown as AxiosResponse
}

function filenameFromDisposition(disposition: unknown): string | null {
  if (typeof disposition !== 'string') return null
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)
  if (!match) return null
  try {
    return decodeURIComponent(match[1])
  } catch {
    return match[1]
  }
}

// A failed download wraps the backend's JSON error body in a Blob, so
// data.message is undefined — decode it to recover the real message (e.g. 429).
async function serverErrorMessage(data: unknown): Promise<string | undefined> {
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    try {
      const parsed = JSON.parse(await data.text())
      return typeof parsed?.message === 'string' ? parsed.message : undefined
    } catch {
      return undefined
    }
  }
  const message = (data as { message?: unknown } | undefined)?.message
  return typeof message === 'string' ? message : undefined
}

/** Never poll the refresh endpoint faster than this, however stale the token looks. */
const MIN_REFRESH_DELAY_MS = 5000

interface Waiter {
  resolve: (token: string) => void
  reject: (reason: unknown) => void
}

export function createApiClient(options: ApiClientOptions): ApiClient {
  const { tokenKey, refreshTokenKey, refreshUrl, loginPath } = options

  const instance = axios.create({ baseURL: '/api/v1', timeout: 15000 })

  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = tokenStore.get(tokenKey)
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
  })

  let isRefreshing = false
  // Requests parked while a refresh is in flight. Both callbacks are kept: a
  // failed refresh must reject them, or their promises never settle and the
  // caller (and its spinner) waits forever.
  let waiters: Waiter[] = []
  const settleWaiters = (settle: (w: Waiter) => void) => {
    const pending = waiters
    waiters = []
    pending.forEach(settle)
  }

  async function refreshAccessToken(): Promise<string> {
    const refreshToken = tokenStore.get(refreshTokenKey)
    if (!refreshToken) throw new Error('No refresh token')
    const res = await axios.post<Result<{ token?: string; refreshToken?: string }>>(
      refreshUrl, { refreshToken })
    const token = res.data?.data?.token
    if (!token) throw new Error('Token refresh returned no token')
    tokenStore.set(tokenKey, token)
    const rotated = res.data.data?.refreshToken
    if (rotated) tokenStore.set(refreshTokenKey, rotated)
    return token
  }

  function endSession() {
    tokenStore.remove(tokenKey)
    tokenStore.remove(refreshTokenKey)
    window.location.href = loginPath
  }

  instance.interceptors.response.use(
    async (res: AxiosResponse<Result<unknown> | Blob>) => {
      if (res.config.responseType === 'blob') {
        return asAxiosResponse({
          blob: res.data,
          filename: filenameFromDisposition(res.headers?.['content-disposition']),
        } as BlobDownload)
      }
      const result = res.data as Result<unknown>
      return asAxiosResponse(result.code === 200 ? result.data : Promise.reject(new Error(result.message)))
    },
    async (err: AxiosError<{ message?: string }>) => {
      const originalRequest = err.config
      if (!originalRequest) return Promise.reject(err)

      if (err.response?.status === 401 && !originalRequest._retry) {
        originalRequest._retry = true

        if (!tokenStore.get(refreshTokenKey)) {
          if (!originalRequest.silent) endSession()
        } else if (isRefreshing) {
          try {
            const token = await new Promise<string>((resolve, reject) => waiters.push({ resolve, reject }))
            originalRequest.headers.Authorization = `Bearer ${token}`
            return instance(originalRequest)
          } catch (refreshError) {
            return Promise.reject(refreshError)
          }
        } else {
          isRefreshing = true
          try {
            const token = await refreshAccessToken()
            scheduleProactiveRefresh()
            settleWaiters(w => w.resolve(token))
            originalRequest.headers.Authorization = `Bearer ${token}`
            return instance(originalRequest)
          } catch (refreshError) {
            settleWaiters(w => w.reject(refreshError))
            if (!originalRequest.silent) endSession()
            return Promise.reject(refreshError)
          } finally {
            isRefreshing = false
          }
        }
      }

      // Server message first — it is more specific than the generic 429 text
      // below (e.g. "Export rate limit exceeded. Max 5 exports per hour.").
      const serverMessage = await serverErrorMessage(err.response?.data)
      if (serverMessage) return Promise.reject(new Error(serverMessage))
      if (err.response?.status === 429) {
        const retryAfter = err.response.headers['retry-after']
        const msg = retryAfter ? `Rate limited. Try again in ${retryAfter}s.` : 'Too many requests. Please wait.'
        return Promise.reject(new Error(msg))
      }
      return Promise.reject(err)
    }
  )

  let proactiveTimer: ReturnType<typeof setTimeout> | null = null

  // Refresh the access token at 80% of its TTL so expiry never surfaces as a
  // visible 401. The token is re-read at fire time to survive rotation.
  function scheduleProactiveRefresh() {
    if (proactiveTimer) clearTimeout(proactiveTimer)
    const token = tokenStore.get(tokenKey)
    if (!token) return
    // A floor on the delay: a token without `exp` (or an already-expired one)
    // yields 0, which would re-arm this timer immediately and hammer /refresh.
    const delay = Math.max(scheduleDelayMs(token), MIN_REFRESH_DELAY_MS)
    proactiveTimer = setTimeout(() => {
      void (async () => {
        try {
          await refreshAccessToken()
        } catch {
          // Leave the tokens in place — the 401 chain handles a stale session.
        }
        scheduleProactiveRefresh()
      })()
    }, delay)
  }

  const http = {
    get: <T>(url: string, config?: AxiosRequestConfig) => instance.get(url, config) as Promise<T>,
    post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => instance.post(url, data, config) as Promise<T>,
    put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => instance.put(url, data, config) as Promise<T>,
    delete: <T>(url: string, config?: AxiosRequestConfig) => instance.delete(url, config) as Promise<T>,
  }

  return { instance, http, scheduleProactiveRefresh }
}
