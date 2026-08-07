import { http } from './request'
import { PageResult } from '../types/common'
import { BillVO, BillCreatePayload, BillQuery } from '../types/entities'

export const getBillPage = (params: BillQuery) => http.get<PageResult<BillVO>>('/bills', { params })
export const createBill = (data: BillCreatePayload) => http.post<BillVO>('/bills', data)
export const submitBill = (id: number) => http.put<void>(`/bills/${id}/submit`)
export const adjudicateBill = (id: number, data: { insurancePayment: number; adjustment: number; claimNumber?: string; adjudicationDate?: string }) => http.put<void>(`/bills/${id}/adjudicate`, data)
export const payBill = (id: number, data: { paymentAmount: number; paymentMethod: string }) => http.put<void>(`/bills/${id}/pay`, data)
export const denyBill = (id: number, reason: string) => http.put<void>(`/bills/${id}/deny`, { reason })
export const deleteBill = (id: number) => http.delete<void>(`/bills/${id}`)
