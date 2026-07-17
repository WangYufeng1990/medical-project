import request from './request'
export const getPrescriptionPage = (params: any) => request.get('/prescriptions', { params })
export const getPrescriptionById = (id: number) => request.get(`/prescriptions/${id}`)
export const createPrescription = (data: any) => request.post('/prescriptions', data)
export const deletePrescription = (id: number) => request.delete(`/prescriptions/${id}`)
export const transmitPrescription = (id: number, pharmacyId: number) => request.put(`/prescriptions/${id}/transmit`, null, { params: { pharmacyId } })
export const getPrescriptionsByPatientId = (patientId: number) => request.get(`/prescriptions/by-patient/${patientId}`)
export const cancelPrescription = (id: number) => request.put(`/prescriptions/${id}/cancel`)
