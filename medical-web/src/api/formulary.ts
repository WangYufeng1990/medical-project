import request from './request'

export const checkFormulary = (rxnormCode: string, insurancePayer: string) =>
  request.get('/formulary/check', { params: { rxnormCode, insurancePayer } })
