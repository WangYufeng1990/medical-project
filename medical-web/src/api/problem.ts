import { http } from './request'
import { PageQuery, PageResult } from '../types/common'
import { ProblemVO, ProblemCreatePayload } from '../types/entities'

export const getProblems = (patientId: number, params?: PageQuery) =>
  http.get<PageResult<ProblemVO>>(`/patients/${patientId}/problems`, { params })

export const createProblem = (patientId: number, data: ProblemCreatePayload) =>
  http.post<ProblemVO>(`/patients/${patientId}/problems`, data)

export const updateProblem = (patientId: number, id: number, data: Partial<ProblemCreatePayload>) =>
  http.put<ProblemVO>(`/patients/${patientId}/problems/${id}`, data)
