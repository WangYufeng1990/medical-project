import request from './request'
export const getBillPage = (params: any) => request.get('/bills', { params })
export const createBill = (data: any) => request.post('/bills', data)
export const submitBill = (id: number) => request.put(`/bills/${id}/submit`)
export const adjudicateBill = (id: number, data: any) => request.put(`/bills/${id}/adjudicate`, data)
export const payBill = (id: number, data: any) => request.put(`/bills/${id}/pay`, data)
export const denyBill = (id: number, reason: string) => request.put(`/bills/${id}/deny`, { reason })
export const deleteBill = (id: number) => request.delete(`/bills/${id}`)
