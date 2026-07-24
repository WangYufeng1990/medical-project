import request from './request'

export const getChargePage = (params?: any) => request.get('/charges', { params })
export const createCharge = (data: any) => request.post('/charges', data)
export const convertCharge = (id: number) => request.put(`/charges/${id}/convert`)
