import request from './request'
export const getBillPage = (params: any) => request.get('/bills', { params })
export const getBillById = (id: number) => request.get(`/bills/${id}`)
export const createBill = (data: any) => request.post('/bills', data)
export const deleteBill = (id: number) => request.delete(`/bills/${id}`)
