import { http } from './request'
import { PageResult } from '../types/common'
import { ConversationVO, MessageVO, SseTicketVO } from '../types/entities'

export const getConversations = (page = 1, size = 20) => http.get<PageResult<ConversationVO>>('/messages/conversations', { params: { page, size } })
export const getMessages = (partnerId: number, page = 1, size = 50) => http.get<PageResult<MessageVO>>(`/messages/${partnerId}`, { params: { page, size } })
export const sendMessage = (receiverId: number, content: string) => http.post<MessageVO>('/messages', { receiverId, content })
export const getSseTicket = () => http.post<SseTicketVO>('/chat/sse-ticket', null, { silent: true })
