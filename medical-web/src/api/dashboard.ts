import { http } from './request'
import { DashboardStats } from '../types/entities'

export const getDashboardStats = () => http.get<DashboardStats>('/dashboard/stats')
