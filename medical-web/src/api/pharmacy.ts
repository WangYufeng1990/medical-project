import { http } from './request'
import { PharmacyVO, PharmacyQuery } from '../types/entities'

export const getPharmacies = (params?: PharmacyQuery) => http.get<PharmacyVO[]>('/pharmacies', { params })
