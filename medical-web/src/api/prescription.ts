import { http } from './request'
import { PageResult } from '../types/common'
import { PrescriptionVO, PrescriptionCreatePayload, PrescriptionQuery } from '../types/entities'

export const getPrescriptionPage = (params: PrescriptionQuery) => http.get<PageResult<PrescriptionVO>>('/prescriptions', { params })
export const getPrescriptionById = (id: number) => http.get<PrescriptionVO>(`/prescriptions/${id}`)
export const createPrescription = (data: PrescriptionCreatePayload) => http.post<PrescriptionVO>('/prescriptions', data)
export const deletePrescription = (id: number) => http.delete<void>(`/prescriptions/${id}`)
export const transmitPrescription = (id: number, pharmacyId: number) => http.put<void>(`/prescriptions/${id}/transmit`, null, { params: { pharmacyId } })
export const getPrescriptionsByPatientId = (patientId: number) => http.get<PrescriptionVO[]>(`/prescriptions/by-patient/${patientId}`)
export const cancelPrescription = (id: number) => http.put<void>(`/prescriptions/${id}/cancel`)
