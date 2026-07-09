import request from './request'

export const getConsents = (patientId: number) =>
  request.get('/consent', { params: { patientId } })

export const createConsent = (data: { patientId: number; consentType: string; scope: string }) =>
  request.post('/consent', data)

export const revokeConsent = (id: number) =>
  request.put(`/consent/${id}/revoke`)
