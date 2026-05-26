import patientRequest from './patientRequest'

export function getMyProfile() {
  return patientRequest.get('/patient/me')
}

export function getMyAppointments(params) {
  return patientRequest.get('/patient/me/appointments', { params })
}

export function getMyPrescriptions(params) {
  return patientRequest.get('/patient/me/prescriptions', { params })
}

export function getMyBills(params) {
  return patientRequest.get('/patient/me/bills', { params })
}
