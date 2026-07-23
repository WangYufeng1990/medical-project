import request from './request'

export const getVitalSigns = (patientId: number, params?: any) =>
  request.get(`/patients/${patientId}/vitals`, { params })

export const createVitalSign = (patientId: number, data: any) =>
  request.post(`/patients/${patientId}/vitals`, data)
