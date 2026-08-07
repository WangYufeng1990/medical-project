import { http } from './request'
import { PageResult } from '../types/common'
import { ObservationVO, ObservationQuery, LoincEntry } from '../types/entities'

export const getObservations = (patientId: number, params?: ObservationQuery) =>
  http.get<PageResult<ObservationVO>>(`/patients/${patientId}/observations`, { params })

export const getObservationTrend = (patientId: number, loinc: string) =>
  http.get<ObservationVO[]>(`/patients/${patientId}/observations/trend`, { params: { loinc } })

export const getLoincCatalog = () => http.get<LoincEntry[]>('/loinc/catalog')

export const getLoincPanel = (parentCode: string) => http.get<LoincEntry[]>(`/loinc/panel/${parentCode}`)
