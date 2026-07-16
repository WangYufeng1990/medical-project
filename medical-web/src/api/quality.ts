import request from './request'
export const getMeasures = () => request.get('/quality/measures')
export const getMeasureReport = (cmsId: string) => request.get(`/quality/measures/${cmsId}/report`)
