import axios from 'axios'

const patientRequest = axios.create({ baseURL: '/api/v1', timeout: 15000 })

patientRequest.interceptors.request.use((config: any) => {
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

patientRequest.interceptors.response.use(
  (res: any) => res,
  async (err: any) => {
    const originalRequest = err.config
    if (err.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true
      const refreshToken = localStorage.getItem('patientRefreshToken')
      if (refreshToken) {
        if (!isRefreshing) {
          isRefreshing = true
          try {
            const res = await axios.post('/api/v1/patient/refresh', { refreshToken })
            const newToken = res.data.data.token
            localStorage.setItem('patientToken', newToken)
            if (res.data.data.refreshToken) {
              localStorage.setItem('patientRefreshToken', res.data.data.refreshToken)
            }
            onRefreshed(newToken)
            isRefreshing = false
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            return patientRequest(originalRequest)
          } catch {
            isRefreshing = false
            refreshSubscribers = []
            localStorage.removeItem('patientToken')
            localStorage.removeItem('patientRefreshToken')
            window.location.href = '/patient/login'
            return Promise.reject(err)
          }
        } else {
          return new Promise(resolve => {
            subscribeTokenRefresh((token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`
              resolve(patientRequest(originalRequest))
            })
          })
        }
      }
      localStorage.removeItem('patientToken')
      window.location.href = '/patient/login'
    }
    return Promise.reject(err)
  }
)

export default patientRequest
