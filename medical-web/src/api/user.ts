import { http } from './request'
import { PageResult } from '../types/common'
import { SysUserVO, SysUserForm, UserQuery, StaffProfileVO, StaffProfileForm, ChangePasswordForm, DoctorVO } from '../types/entities'

export const getUserPage = (params: UserQuery) => http.get<PageResult<SysUserVO>>('/users', { params })
export const getUserById = (id: number) => http.get<SysUserVO>(`/users/${id}`)
export const createUser = (data: SysUserForm) => http.post<SysUserVO>('/users', data)
export const updateUser = (id: number, data: SysUserForm) => http.put<SysUserVO>(`/users/${id}`, data)
export const deleteUser = (id: number) => http.delete<void>(`/users/${id}`)
export const unlockUser = (id: number) => http.put<void>(`/users/${id}/unlock`)
export const getProfile = () => http.get<StaffProfileVO>('/users/me')
export const updateProfile = (data: StaffProfileForm) => http.put<StaffProfileVO>('/users/me', data)
export const changePassword = (data: ChangePasswordForm) => http.put<void>('/users/me/password', data)
export const getDoctors = () => http.get<DoctorVO[]>('/users/doctors')
