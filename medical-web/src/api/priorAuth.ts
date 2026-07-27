import request from './request'

export const getPriorAuths = (params?: any) => request.get('/prior-auths', { params })
export const createPriorAuth = (data: any) => request.post('/prior-auths', data)
export const updatePriorAuth = (id: number, data: any) => request.put(`/prior-auths/${id}`, data)
