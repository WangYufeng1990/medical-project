import { http } from './request'
import { ConsentVO } from '../types/entities'

export const getConsents = (patientId: number) => http.get<ConsentVO[]>('/consent', { params: { patientId } })

export const createConsent = (data: { patientId: number; consentType: string; scope: string }) =>
  http.post<ConsentVO>('/consent', data)

export const revokeConsent = (id: number) => http.put<void>(`/consent/${id}/revoke`)
