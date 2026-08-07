import { http } from './request'
import { PageResult } from '../types/common'
import { AuditLogVO, AuditDistinctValues, AuditQuery } from '../types/entities'

export const getAuditLogs = (params?: AuditQuery) => http.get<PageResult<AuditLogVO>>('/audit-logs', { params })

export const getDistinctValues = () => http.get<AuditDistinctValues>('/audit-logs/distinct-values')
