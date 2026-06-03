import request from './request'

export function getMenuTree() {
  return request.get('/menus/tree')
}

export function getAllMenus() {
  return request.get('/menus')
}

export function createMenu(data) {
  return request.post('/menus', data)
}

export function updateMenu(id, data) {
  return request.put(`/menus/${id}`, data)
}

export function deleteMenu(id) {
  return request.delete(`/menus/${id}`)
}
