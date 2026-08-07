import { http } from './request'
import { CdsCheckRequest, CdsCheckResult, DrugLookupResult } from '../types/entities'

export const checkCds = (data: CdsCheckRequest) => http.post<CdsCheckResult>('/cds/check', data)
export const lookupDrug = (rxnorm: string) => http.get<DrugLookupResult>('/cds/drugs', { params: { rxnorm } })
