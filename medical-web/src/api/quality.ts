import request from './request'
export const getMeasures = () => request.get('/quality/measures')
export const getMeasureReport = (cmsId: string) => request.get(`/quality/measures/${cmsId}/report`)
export const calculateMeasureReport = (cmsId: string) => request.post(`/quality/measures/${cmsId}/calculate`)
export const getMeasureHistory = (cmsId: string) => request.get(`/quality/measures/${cmsId}/history`)
