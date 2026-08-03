import request from './request'
export const getAppointmentPage = (params: any) => request.get('/appointments', { params })
export const getAppointmentById = (id: number) => request.get(`/appointments/${id}`)
export const getAppointmentConflicts = (params: any) => request.get('/appointments/conflicts', { params })
export const createAppointment = (data: any) => request.post('/appointments', data)
export const updateAppointment = (id: number, data: any) => request.put(`/appointments/${id}`, data)
export const deleteAppointment = (id: number) => request.delete(`/appointments/${id}`)
