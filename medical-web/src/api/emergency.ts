import { http } from './request'
import { EmergencyAccessVO, EmergencyAccessResultVO } from '../types/entities'

export const initiateEmergencyAccess = (patientId: number, reason: string) =>
  http.post<EmergencyAccessResultVO>(`/emergency/access/${patientId}`, { reason })

export const getEmergencyHistory = (params?: { patientId?: number; audited?: number }) =>
  http.get<EmergencyAccessVO[]>('/emergency/history', { params })

export const reviewEmergencyAccess = (id: number) => http.put<void>(`/emergency/${id}/review`)
