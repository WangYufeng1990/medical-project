import request from './request'

export const getImmunizations = (patientId: number, params?: any) =>
  request.get(`/patients/${patientId}/immunizations`, { params })

export const createImmunization = (patientId: number, data: any) =>
  request.post(`/patients/${patientId}/immunizations`, data)
