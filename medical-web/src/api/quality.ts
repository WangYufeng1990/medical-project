import { http } from './request'
import { QualityMeasureVO, QualityResultVO } from '../types/entities'

export const getMeasures = () => http.get<QualityMeasureVO[]>('/quality/measures')
export const getMeasureReport = (cmsId: string) => http.get<QualityResultVO>(`/quality/measures/${cmsId}/report`)
export const calculateMeasureReport = (cmsId: string) => http.post<QualityResultVO>(`/quality/measures/${cmsId}/calculate`)
export const getMeasureHistory = (cmsId: string) => http.get<QualityResultVO[]>(`/quality/measures/${cmsId}/history`)
