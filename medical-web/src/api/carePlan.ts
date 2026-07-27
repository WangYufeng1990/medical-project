import request from './request'

export const getCarePlans = (patientId: number, params?: any) => request.get(`/patients/${patientId}/care-plans`, { params })
export const createCarePlan = (patientId: number, data: any) => request.post(`/patients/${patientId}/care-plans`, data)
export const updateCarePlan = (patientId: number, id: number, data: any) => request.put(`/patients/${patientId}/care-plans/${id}`, data)
