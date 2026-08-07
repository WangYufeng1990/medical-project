import { http } from './request'
import { PageQuery, PageResult } from '../types/common'
import { ChargeVO, ChargeCreatePayload } from '../types/entities'

export const getChargePage = (params?: PageQuery) => http.get<PageResult<ChargeVO>>('/charges', { params })
export const createCharge = (data: ChargeCreatePayload) => http.post<ChargeVO>('/charges', data)
export const convertCharge = (id: number) => http.put<void>(`/charges/${id}/convert`)
