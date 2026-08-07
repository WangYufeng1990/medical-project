import { http } from './request'
import { PageQuery, PageResult } from '../types/common'
import { VitalSignVO, VitalSignCreatePayload } from '../types/entities'

export const getVitalSigns = (patientId: number, params?: PageQuery) =>
  http.get<PageResult<VitalSignVO>>(`/patients/${patientId}/vitals`, { params })

export const createVitalSign = (patientId: number, data: VitalSignCreatePayload) =>
  http.post<VitalSignVO>(`/patients/${patientId}/vitals`, data)
