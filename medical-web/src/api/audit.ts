import request from './request'

export const getAuditLogs = (params?: {
  page?: number
  size?: number
  module?: string
  action?: string
  userId?: number
  patientId?: number
  fromDate?: string
  toDate?: string
}) => request.get('/audit-logs', { params })

export const getDistinctValues = () => request.get('/audit-logs/distinct-values')
