import request from './request'
export const getConversations = (page = 1, size = 20) => request.get('/messages/conversations', { params: { page, size } })
export const getMessages = (partnerId: number, page = 1, size = 50) => request.get(`/messages/${partnerId}`, { params: { page, size } })
export const sendMessage = (receiverId: number, content: string) => request.post('/messages', { receiverId, content })
export const getSseTicket = () => request.post('/chat/sse-ticket', null, { silent: true } as any)
