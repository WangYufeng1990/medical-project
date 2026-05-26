import request from './request'

export function getPrescriptionPage(params) {
  return request.get('/prescriptions', { params })
}

export function getPrescriptionById(id) {
  return request.get(`/prescriptions/${id}`)
}

export function createPrescription(data) {
  return request.post('/prescriptions', data)
}

export function deletePrescription(id) {
  return request.delete(`/prescriptions/${id}`)
}
