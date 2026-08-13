import { http } from './request'
import { PageResult } from '../types/common'
import { ConversationVO, MessageVO, SseTicketVO } from '../types/entities'

export type PartyType = 'STAFF' | 'PATIENT'

export const getConversations = (page = 1, size = 20) => http.get<PageResult<ConversationVO>>('/messages/conversations', { params: { page, size } })
export const getMessages = (partnerId: number, partnerType: PartyType, page = 1, size = 50) => http.get<PageResult<MessageVO>>(`/messages/${partnerId}`, { params: { partnerType, page, size } })
export const sendMessage = (receiverId: number, receiverType: PartyType, content: string) => http.post<MessageVO>('/messages', { receiverId, receiverType, content })
export const getUnreadCount = () => http.get<number>('/messages/unread-count')
export const getSseTicket = () => http.post<SseTicketVO>('/chat/sse-ticket', null, { silent: true })
