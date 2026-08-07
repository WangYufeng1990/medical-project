import { http } from './request'
import { RefillRequestVO } from '../types/entities'

export const getPendingRefillRequests = () => http.get<RefillRequestVO[]>('/prescriptions/refill-requests')
export const approveRefillRequest = (id: number) => http.put<void>(`/prescriptions/refill-requests/${id}/approve`)
export const denyRefillRequest = (id: number, notes?: string) => http.put<void>(`/prescriptions/refill-requests/${id}/deny`, { notes })
