import request from './request'
export const checkCds = (data: any) => request.post('/cds/check', data)
