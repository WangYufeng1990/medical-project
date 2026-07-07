import request from './request'
export const getPharmacies = (params?: any) => request.get('/pharmacies', { params })
