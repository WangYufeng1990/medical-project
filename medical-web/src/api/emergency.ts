import request from './request'

export const initiateEmergencyAccess = (patientId: number, reason: string) =>
  request.post(`/emergency/access/${patientId}`, { reason })

export const getEmergencyHistory = (params?: { patientId?: number; audited?: number }) =>
  request.get('/emergency/history', { params })

export const reviewEmergencyAccess = (id: number) =>
  request.put(`/emergency/${id}/review`)
