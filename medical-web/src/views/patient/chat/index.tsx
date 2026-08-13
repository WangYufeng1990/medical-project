import { useState, useEffect, useRef, useCallback } from 'react'
import { http } from '../../../api/patientRequest'
import { useChatSse } from '../../../hooks/useChatSse'
import { parseJwt } from '../../../utils/auth'
import { PageResult } from '../../../types/common'
import { MessageVO, ConversationVO, SseTicketVO } from '../../../types/entities'
import chatStyles from '../../chat/style.module.css'

export default function PatientChat() {
  const [conversations, setConversations] = useState<ConversationVO[]>([])
  const [selectedPartner, setSelectedPartner] = useState<{ id: number; name: string } | null>(null)
  const [messages, setMessages] = useState<MessageVO[]>([])
  const [msgPage, setMsgPage] = useState(1)
  const [msgTotal, setMsgTotal] = useState(0)
  const [input, setInput] = useState('')
  const messageListRef = useRef<HTMLDivElement>(null)

  const token = localStorage.getItem('patientToken')
  const raw = token ? parseJwt(token) : {}
  const currentUserId = Number(raw.uid ?? raw.jti ?? 0)

  const loadConversations = useCallback(() => {
    http.get<PageResult<ConversationVO>>('/patient/me/messages/conversations', { params: { page: 1, size: 20 } })
      .then(r => setConversations(r.records ?? []))
  }, [])

  useEffect(() => { loadConversations() }, [loadConversations])

  const loadMessages = useCallback((partnerId: number, page: number) => {
    return http.get<PageResult<MessageVO>>(`/patient/me/messages/${partnerId}`, { params: { page, size: 50 } })
      .then(r => {
        const batch = (r.records ?? []).reverse()
        setMessages(prev => page === 1 ? batch : [...batch, ...prev])
        setMsgTotal(r.total ?? 0)
      })
  }, [])

  const selectPartner = (p: { partnerId: number; partnerName: string }) => {
    setSelectedPartner({ id: p.partnerId, name: p.partnerName })
    setMsgPage(1)
    setMessages([])
    loadMessages(p.partnerId, 1).then(() => loadConversations())
  }

  const handleSend = async () => {
    if (!input.trim() || !selectedPartner) return
    const content = input.trim()
    setInput('')
    const msg: MessageVO = { id: Date.now(), senderId: currentUserId, senderType: 'PATIENT', receiverId: selectedPartner.id, receiverType: 'STAFF', content, isRead: false, createTime: new Date().toISOString() }
    setMessages(prev => [...prev, msg])
    await http.post<MessageVO>('/patient/me/messages', { receiverId: selectedPartner.id, content })
      .catch(() => setMessages(prev => prev.filter(m => m.id !== msg.id)))
    setTimeout(() => {
      messageListRef.current?.scrollTo({ top: messageListRef.current.scrollHeight, behavior: 'smooth' })
    }, 50)
  }

  const handleSseMessage = useCallback((msg: MessageVO) => {
    if (selectedPartner && (
      (msg.senderType === 'STAFF' && msg.senderId === selectedPartner.id) ||
      (msg.receiverType === 'STAFF' && msg.receiverId === selectedPartner.id))) {
      setMessages(prev => { if (prev.find(m => m.id === msg.id)) return prev; return [...prev, msg] })
      setTimeout(() => {
        messageListRef.current?.scrollTo({ top: messageListRef.current.scrollHeight, behavior: 'smooth' })
      }, 50)
    }
    loadConversations()
  }, [selectedPartner, loadConversations])

  useChatSse(() => http.post<SseTicketVO>('/chat/sse-ticket', null, { silent: true }).then(r => r.ticket), currentUserId, handleSseMessage)

  useEffect(() => {
    if (messageListRef.current) messageListRef.current.scrollTop = messageListRef.current.scrollHeight
  }, [messages.length])

  const hasMore = messages.length < msgTotal

  const formatTime = (t: string) => {
    if (!t) return ''
    const d = new Date(t)
    return d.toDateString() === new Date().toDateString()
      ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      : d.toLocaleDateString()
  }

  return (
    <div className={chatStyles.wrapper}>
      <div className={chatStyles.sidebar}>
        <div className={chatStyles.sidebarHeader}>Messages</div>
        <div className={chatStyles.conversationList}>
          {conversations.map(c => (
            <div key={c.partnerId}
              className={`${chatStyles.conversationItem} ${selectedPartner?.id === c.partnerId ? chatStyles.conversationItemActive : ''}`}
              onClick={() => selectPartner(c)}>
              <div className={chatStyles.partnerName}>{c.partnerName}</div>
              <div className={chatStyles.lastMsg}>{c.lastMessage}</div>
              <div className={chatStyles.conversationMeta}>
                <span className={chatStyles.conversationTime}>{formatTime(c.lastMessageTime || '')}</span>
                {(c.unreadCount ?? 0) > 0 && <span className={chatStyles.unreadBadge}>{c.unreadCount}</span>}
              </div>
            </div>
          ))}
          {conversations.length === 0 && <div style={{ padding: 24, textAlign: 'center', color: '#c0c4cc', fontSize: 13 }}>No conversations</div>}
        </div>
      </div>
      <div className={chatStyles.chatArea}>
        {!selectedPartner ? (
          <div className={chatStyles.chatPlaceholder}>Select a conversation</div>
        ) : (
          <>
            <div className={chatStyles.chatHeader}>{selectedPartner.name}</div>
            <div className={chatStyles.messageList} ref={messageListRef}>
              {hasMore && (
                <div className={chatStyles.loadMore}>
                  <button onClick={() => { const np = msgPage + 1; setMsgPage(np); loadMessages(selectedPartner.id, np) }}>Load earlier</button>
                </div>
              )}
              {messages.map(m => {
                const isMe = m.senderType === 'PATIENT' && m.senderId === currentUserId
                return (
                <div key={m.id} className={`${chatStyles.messageRow} ${isMe ? chatStyles.messageRowMe : chatStyles.messageRowOther}`}>
                  <div>
                    {!isMe && <div className={chatStyles.senderName}>{selectedPartner!.name}</div>}
                    <div className={`${chatStyles.messageBubble} ${isMe ? chatStyles.messageBubbleMe : chatStyles.messageBubbleOther}`}>{m.content}</div>
                    <div className={`${chatStyles.messageTime} ${isMe ? chatStyles.messageTimeMe : ''}`}>{formatTime(m.createTime)}</div>
                  </div>
                </div>
                )})}
            </div>
            <div className={chatStyles.inputArea}>
              <input value={input} onChange={e => setInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') handleSend() }} placeholder="Type a message..." />
              <button onClick={handleSend}>Send</button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
