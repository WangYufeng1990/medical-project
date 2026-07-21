import request from './request'

export const getKeyHistory = () => request.get('/admin/keys/history')
export const getRotationStatus = () => request.get('/admin/keys/rotation-status')
export const rotateKey = (data: { newKey: string; oldKey: string }) => request.post('/admin/keys/rotate', data)
