import { http } from './request'
import { PageResult } from '../types/common'
import { AppointmentVO, AppointmentForm, AppointmentQuery, AppointmentConflict } from '../types/entities'

export const getAppointmentPage = (params: AppointmentQuery) => http.get<PageResult<AppointmentVO>>('/appointments', { params })
export const getAppointmentById = (id: number) => http.get<AppointmentVO>(`/appointments/${id}`)
export const getAppointmentConflicts = (params: { doctorId: number; time: string; excludeId?: number }) => http.get<AppointmentConflict[]>('/appointments/conflicts', { params })
export const createAppointment = (data: AppointmentForm) => http.post<AppointmentVO>('/appointments', data)
export const updateAppointment = (id: number, data: AppointmentForm) => http.put<AppointmentVO>(`/appointments/${id}`, data)
export const deleteAppointment = (id: number) => http.delete<void>(`/appointments/${id}`)
