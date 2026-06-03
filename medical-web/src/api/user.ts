import request from './request'
export const getUserPage = (params: any) => request.get('/users', { params })
export const getUserById = (id: number) => request.get(`/users/${id}`)
export const createUser = (data: any) => request.post('/users', data)
export const updateUser = (id: number, data: any) => request.put(`/users/${id}`, data)
export const deleteUser = (id: number) => request.delete(`/users/${id}`)
export const getProfile = () => request.get('/users/me')
export const updateProfile = (data: any) => request.put('/users/me', data)
export const changePassword = (data: any) => request.put('/users/me/password', data)
