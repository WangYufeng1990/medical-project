import request from './request'

export function getProfile() {
  return request.get('/users/me')
}

export function updateProfile(data) {
  return request.put('/users/me', data)
}

export function changePassword(data) {
  return request.put('/users/me/password', data)
}
