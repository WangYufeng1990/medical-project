import { http } from './request'
import { QualityMeasureVO, QualityReportVO, QualityResultVO } from '../types/entities'

export const getMeasures = () => http.get<QualityMeasureVO[]>('/quality/measures')
export const getMeasureReport = (cmsId: string) => http.get<QualityReportVO>(`/quality/measures/${cmsId}/report`)
export const calculateMeasureReport = (cmsId: string) => http.post<QualityReportVO>(`/quality/measures/${cmsId}/calculate`)
export const getMeasureHistory = (cmsId: string) => http.get<QualityResultVO[]>(`/quality/measures/${cmsId}/history`)
