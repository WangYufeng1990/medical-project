import request from './request'
export const getRolePage = (params: any) => request.get('/roles', { params })
export const createRole = (data: any) => request.post('/roles', data)
export const updateRole = (id: number, data: any) => request.put(`/roles/${id}`, data)
export const deleteRole = (id: number) => request.delete(`/roles/${id}`)
