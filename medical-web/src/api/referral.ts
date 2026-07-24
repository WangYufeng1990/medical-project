import request from './request'

export const getReferralPage = (params?: any) => request.get('/referrals', { params })
export const getPatientReferrals = (patientId: number) => request.get(`/patients/${patientId}/referrals`)
export const createReferral = (data: any) => request.post('/referrals', data)
export const updateReferral = (id: number, data: any) => request.put(`/referrals/${id}`, data)
