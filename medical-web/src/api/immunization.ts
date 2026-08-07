import { http } from './request'
import { PageQuery, PageResult } from '../types/common'
import { ImmunizationVO, ImmunizationCreatePayload } from '../types/entities'

export const getImmunizations = (patientId: number, params?: PageQuery) =>
  http.get<PageResult<ImmunizationVO>>(`/patients/${patientId}/immunizations`, { params })

export const createImmunization = (patientId: number, data: ImmunizationCreatePayload) =>
  http.post<ImmunizationVO>(`/patients/${patientId}/immunizations`, data)
