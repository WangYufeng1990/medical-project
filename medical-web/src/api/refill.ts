import request from './request'

export const getPendingRefillRequests = () => request.get('/prescriptions/refill-requests')
export const approveRefillRequest = (id: number) => request.put(`/prescriptions/refill-requests/${id}/approve`)
export const denyRefillRequest = (id: number, notes?: string) => request.put(`/prescriptions/refill-requests/${id}/deny`, { notes })
