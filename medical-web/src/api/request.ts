import axios from 'axios'

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
  (res: any) => res.data.code === 200 ? res.data.data : Promise.reject(new Error(res.data.message)),
  async (err: any) => {
    const originalRequest = err.config
    if (err.response?.status === 401 && !originalRequest._retry) {
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
            onRefreshed(newToken)
            isRefreshing = false
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return request(originalRequest)
          } catch {
            isRefreshing = false
            refreshSubscribers = []
            localStorage.removeItem('token')
            localStorage.removeItem('refreshToken')
            window.location.href = '/login'
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
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default request
