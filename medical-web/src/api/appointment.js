import request from './request'

export function getAppointmentPage(params) {
  return request.get('/appointments', { params })
}

export function getAppointmentById(id) {
  return request.get(`/appointments/${id}`)
}

export function createAppointment(data) {
  return request.post('/appointments', data)
}

export function updateAppointment(id, data) {
  return request.put(`/appointments/${id}`, data)
}

export function deleteAppointment(id) {
  return request.delete(`/appointments/${id}`)
}
