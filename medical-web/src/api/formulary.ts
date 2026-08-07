import { http } from './request'
import { FormularyEntry } from '../types/entities'

export const checkFormulary = (rxnormCode: string, insurancePayer: string) =>
  http.get<FormularyEntry>('/formulary/check', { params: { rxnormCode, insurancePayer } })
