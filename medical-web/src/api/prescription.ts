import request from './request'
export const getPrescriptionPage = (params: any) => request.get('/prescriptions', { params })
export const getPrescriptionById = (id: number) => request.get(`/prescriptions/${id}`)
export const createPrescription = (data: any) => request.post('/prescriptions', data)
export const updatePrescription = (id: number, data: any) => request.put(`/prescriptions/${id}`, data)
export const deletePrescription = (id: number) => request.delete(`/prescriptions/${id}`)
