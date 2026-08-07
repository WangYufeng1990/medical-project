import { http } from './request'
import { PageQuery, PageResult } from '../types/common'
import { CarePlanVO, CarePlanCreatePayload } from '../types/entities'

export const getCarePlans = (patientId: number, params?: PageQuery) => http.get<PageResult<CarePlanVO>>(`/patients/${patientId}/care-plans`, { params })
export const createCarePlan = (patientId: number, data: CarePlanCreatePayload) => http.post<CarePlanVO>(`/patients/${patientId}/care-plans`, data)
export const updateCarePlan = (patientId: number, id: number, data: Partial<CarePlanCreatePayload>) => http.put<CarePlanVO>(`/patients/${patientId}/care-plans/${id}`, data)
