import axios from 'axios'

const request = axios.create({ baseURL: '/api/v1', timeout: 15000 })

request.interceptors.request.use((config: any) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (res: any) => res.data.code === 200 ? res.data.data : Promise.reject(new Error(res.data.message)),
  (err: any) => {
    if (err.response?.status === 401) { localStorage.removeItem('token'); window.location.href = '/login' }
    return Promise.reject(err)
  }
)

export default request
