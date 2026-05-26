import axios from 'axios'
import { ElMessage } from 'element-plus'

const patientRequest = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

patientRequest.interceptors.request.use((config) => {
  const token = localStorage.getItem('patientToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

patientRequest.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code === 200) {
      return data
    }
    ElMessage.error(message || 'Request failed')
    return Promise.reject(new Error(message))
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('patientToken')
      localStorage.removeItem('patientInfo')
      window.location.href = '/patient/login'
    }
    ElMessage.error(error.message || 'Network error')
    return Promise.reject(error)
  }
)

export default patientRequest
