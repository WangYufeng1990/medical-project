import request from './request'

export const getProblems = (patientId: number, params?: any) =>
  request.get(`/patients/${patientId}/problems`, { params })

export const createProblem = (patientId: number, data: any) =>
  request.post(`/patients/${patientId}/problems`, data)

export const updateProblem = (patientId: number, id: number, data: any) =>
  request.put(`/patients/${patientId}/problems/${id}`, data)
