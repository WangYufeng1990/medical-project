import { http } from './request'
import { PageQuery, PageResult } from '../types/common'
import { ReferralVO, ReferralForm } from '../types/entities'

export const getReferralPage = (params?: PageQuery) => http.get<PageResult<ReferralVO>>('/referrals', { params })
export const getPatientReferrals = (patientId: number) => http.get<ReferralVO[]>(`/patients/${patientId}/referrals`)
export const createReferral = (data: Omit<ReferralForm, 'patientId'> & { patientId: number }) => http.post<ReferralVO>('/referrals', data)
export const updateReferral = (id: number, data: Partial<ReferralForm> & { status?: string }) => http.put<ReferralVO>(`/referrals/${id}`, data)
