import { http } from './request'
import { MenuVO, MenuForm } from '../types/entities'

export const getMenuTree = () => http.get<MenuVO[]>('/menus/tree')
export const getMenuList = () => http.get<MenuVO[]>('/menus')
export const createMenu = (data: MenuForm) => http.post<MenuVO>('/menus', data)
export const updateMenu = (id: number, data: MenuForm) => http.put<MenuVO>(`/menus/${id}`, data)
export const deleteMenu = (id: number) => http.delete<void>(`/menus/${id}`)
