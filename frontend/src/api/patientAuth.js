import patientRequest from './patientRequest'

export function patientLogin(data) {
  return patientRequest.post('/patient/login', data)
}
