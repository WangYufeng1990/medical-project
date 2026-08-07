import { http } from './request'
import { PageQuery, PageResult } from '../types/common'
import { RoleVO, RoleForm } from '../types/entities'

export const getRolePage = (params: PageQuery) => http.get<PageResult<RoleVO>>('/roles', { params })
export const createRole = (data: RoleForm) => http.post<RoleVO>('/roles', data)
export const updateRole = (id: number, data: RoleForm) => http.put<RoleVO>(`/roles/${id}`, data)
export const deleteRole = (id: number) => http.delete<void>(`/roles/${id}`)
