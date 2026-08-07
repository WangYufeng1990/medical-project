import { http } from './request'
import { PageQuery, PageResult } from '../types/common'
import { PriorAuthVO, PriorAuthForm } from '../types/entities'

export const getPriorAuths = (params?: PageQuery) => http.get<PageResult<PriorAuthVO>>('/prior-auths', { params })
export const createPriorAuth = (data: Omit<PriorAuthForm, 'patientId'> & { patientId: number }) => http.post<PriorAuthVO>('/prior-auths', data)
export const updatePriorAuth = (id: number, data: { status?: string; authNumber?: string; resolvedAt?: string }) => http.put<PriorAuthVO>(`/prior-auths/${id}`, data)
