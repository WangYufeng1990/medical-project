import { http } from './request'
import { FormularyEntry } from '../types/entities'

export interface FormularyCheckResult {
  found: boolean
  message?: string
  drugName?: string
  tier?: string
  priorAuthRequired?: boolean
  stepTherapyRequired?: boolean
  alternatives?: string
}

export const checkFormulary = (rxnormCode: string, insurancePayer: string) =>
  http.get<FormularyCheckResult>('/formulary/check', { params: { rxnormCode, insurancePayer } })
