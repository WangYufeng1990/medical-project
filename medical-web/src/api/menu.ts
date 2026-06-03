import request from './request'
export const getMenuTree = () => request.get('/menus/tree')
export const getMenuList = () => request.get('/menus')
export const createMenu = (data: any) => request.post('/menus', data)
export const updateMenu = (id: number, data: any) => request.put(`/menus/${id}`, data)
export const deleteMenu = (id: number) => request.delete(`/menus/${id}`)
