import { http } from './request'
import { PageQuery, PageResult } from '../types/common'
import { PatientVO, PatientForm, MedicalHistoryEntry, AllergyEntry } from '../types/entities'

export const getPatientPage = (params: PageQuery) => http.get<PageResult<PatientVO>>('/patients', { params })
export const getPatientById = (id: number) => http.get<PatientVO>(`/patients/${id}`)
export const createPatient = (data: PatientForm) => http.post<PatientVO>('/patients', data)
export const updatePatient = (id: number, data: PatientForm) => http.put<PatientVO>(`/patients/${id}`, data)
export const deletePatient = (id: number) => http.delete<void>(`/patients/${id}`)
export const getPatientHistory = (patientId: number) => http.get<MedicalHistoryEntry[]>(`/patients/${patientId}/history`)
export const addPatientHistory = (patientId: number, description: string) => http.post<MedicalHistoryEntry>(`/patients/${patientId}/history`, { description })
export const getPatientAllergies = (patientId: number) => http.get<AllergyEntry[]>(`/patients/${patientId}/allergies`)
export const addPatientAllergy = (patientId: number, data: { allergen: string; reaction?: string | null; severity: string }) => http.post<AllergyEntry>(`/patients/${patientId}/allergies`, data)
export const resolvePatientAllergy = (patientId: number, id: number) => http.put<void>(`/patients/${patientId}/allergies/${id}/resolve`)
