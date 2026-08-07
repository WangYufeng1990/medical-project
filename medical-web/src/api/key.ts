import { http } from './request'
import { KeyHistoryEntry, KeyRotationStatusVO } from '../types/entities'

export const getKeyHistory = () => http.get<KeyHistoryEntry[]>('/admin/keys/history')
export const getRotationStatus = () => http.get<KeyRotationStatusVO>('/admin/keys/rotation-status')
export const rotateKey = (data: { newKey: string; oldKey: string }) => http.post<void>('/admin/keys/rotate', data)
