import request from './request'

export const getPatientPage = (params: any) => request.get('/patients', { params })
export const getPatientById = (id: number) => request.get(`/patients/${id}`)
export const createPatient = (data: any) => request.post('/patients', data)
export const updatePatient = (id: number, data: any) => request.put(`/patients/${id}`, data)
export const deletePatient = (id: number) => request.delete(`/patients/${id}`)
export const getPatientCase = (id: number) => request.get(`/patients/${id}/case`)
export const getPatientHistory = (patientId: number) => request.get(`/patients/${patientId}/history`)
export const addPatientHistory = (patientId: number, description: string) => request.post(`/patients/${patientId}/history`, { description })
export const getPatientAllergies = (patientId: number) => request.get(`/patients/${patientId}/allergies`)
export const addPatientAllergy = (patientId: number, data: any) => request.post(`/patients/${patientId}/allergies`, data)
export const removePatientAllergy = (patientId: number, id: number) => request.delete(`/patients/${patientId}/allergies/${id}`)
