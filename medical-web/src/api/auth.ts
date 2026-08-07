import { http } from './request'
import { LoginResponse } from '../types/entities'

export const login = (data: { username: string; password: string }) => http.post<LoginResponse>('/auth/login', data)
export const refresh = (token: string) => http.post<LoginResponse>('/auth/refresh', { refreshToken: token })
export const logout = () => http.post<void>('/auth/logout')
