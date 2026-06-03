import request from './request'

export function getPatientPage(params) {
  return request.get('/patients', { params })
}

export function getPatientById(id) {
  return request.get(`/patients/${id}`)
}

export function createPatient(data) {
  return request.post('/patients', data)
}

export function updatePatient(id, data) {
  return request.put(`/patients/${id}`, data)
}

export function deletePatient(id) {
  return request.delete(`/patients/${id}`)
}

export function getPatientCase(id) {
  return request.get(`/patients/${id}/case`)
}
