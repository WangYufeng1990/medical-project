import { useState, useEffect, useRef, useCallback } from 'react'
import { http } from '../../../api/patientRequest'
import { useChatSse } from '../../../hooks/useChatSse'
import { parseJwt } from '../../../utils/auth'
import { tokenStore } from '../../../utils/auth'
import { PageResult } from '../../../types/common'
import { ChatMessage, MessageVO, ConversationVO, SseTicketVO } from '../../../types/entities'
import { newClientMessageId } from '../../../utils/chat'
import chatStyles from '../../chat/style.module.css'

export default function PatientChat() {
  const [conversations, setConversations] = useState<ConversationVO[]>([])
  const [selectedPartner, setSelectedPartner] = useState<{ id: number; name: string } | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [msgPage, setMsgPage] = useState(1)
  const [msgTotal, setMsgTotal] = useState(0)
  const [input, setInput] = useState('')
  const messageListRef = useRef<HTMLDivElement>(null)
  // Same guard as the staff chat: only the newest load may write into the panel,
  // so a response for a conversation the patient already left cannot replace the
  // one on screen (R53-11).
  const loadSeqRef = useRef(0)

  const token = tokenStore.get('patientToken')
  const raw = token ? parseJwt(token) : {}
  const currentUserId = Number(raw.uid ?? raw.jti ?? 0)

  // A response that lands after the patient switched threads must not be appended
  // to the thread on screen (Round 54.3), and a retry must re-send the same attempt.
  const selectionRef = useRef<{ id: number } | null>(null)
  useEffect(() => { selectionRef.current = selectedPartner ? { id: selectedPartner.id } : null }, [selectedPartner])
  const pendingRef = useRef(new Map<string, { partnerId: number; content: string }>())
  const [sendError, setSendError] = useState('')

  const loadConversations = useCallback(() => {
    http.get<PageResult<ConversationVO>>('/patient/me/messages/conversations', { params: { page: 1, size: 20 } })
      .then(r => setConversations(r.records ?? []))
  }, [])

  useEffect(() => { loadConversations() }, [loadConversations])

  const loadMessages = useCallback((partnerId: number, page: number) => {
    const seq = ++loadSeqRef.current
    return http.get<PageResult<MessageVO>>(`/patient/me/messages/${partnerId}`, { params: { page, size: 50 } })
      .then(r => {
        if (seq !== loadSeqRef.current) return // a newer load owns the panel now
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

  const keyOf = (m: ChatMessage) => m.clientMessageId ?? ''

  const scrollToBottom = () => {
    setTimeout(() => {
      messageListRef.current?.scrollTo({ top: messageListRef.current.scrollHeight, behavior: 'smooth' })
    }, 50)
  }

  const settle = (clientMessageId: string, sent: MessageVO) => {
    pendingRef.current.delete(clientMessageId)
    const selected = selectionRef.current
    const belongsHere = selected != null && selected.id === sent.receiverId && sent.receiverType === 'STAFF'
    setMessages(prev => {
      const withoutPlaceholder = prev.filter(m => keyOf(m) !== clientMessageId)
      return belongsHere ? [...withoutPlaceholder, sent] : withoutPlaceholder
    })
    setSendError('')
    loadConversations()
  }

  const fail = () => {
    setSendError('Message could not be sent. Retry from the message, or copy the text and try again.')
  }

  const sendAttempt = async (clientMessageId: string, partnerId: number, content: string) => {
    setMessages(prev => [...prev, {
      id: -Date.now(), clientMessageId, status: 'sending',
      senderId: currentUserId, senderType: 'PATIENT',
      receiverId: partnerId, receiverType: 'STAFF',
      content, isRead: false, createTime: new Date().toISOString(),
    }])
    scrollToBottom()
    try {
      const sent = await http.post<MessageVO>('/patient/me/messages', { receiverId: partnerId, content, clientMessageId })
      settle(clientMessageId, sent)
    } catch {
      setMessages(prev => prev.map(m => keyOf(m) === clientMessageId ? { ...m, status: 'failed' } : m))
      fail()
    }
  }

  const handleSend = () => {
    if (!input.trim() || !selectedPartner) return
    const content = input.trim()
    const clientMessageId = newClientMessageId()
    pendingRef.current.set(clientMessageId, { partnerId: selectedPartner.id, content })
    setInput('')
    setSendError('')
    void sendAttempt(clientMessageId, selectedPartner.id, content)
  }

  const handleRetry = (m: ChatMessage) => {
    const pending = m.clientMessageId ? pendingRef.current.get(m.clientMessageId) : undefined
    if (!pending || !m.clientMessageId) return
    setMessages(prev => prev.map(x => keyOf(x) === m.clientMessageId ? { ...x, status: 'sending' } : x))
    void sendAttempt(m.clientMessageId, pending.partnerId, pending.content)
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
                <div key={`${m.id}-${m.clientMessageId ?? ''}`} className={`${chatStyles.messageRow} ${isMe ? chatStyles.messageRowMe : chatStyles.messageRowOther}`}>
                  <div>
                    {!isMe && <div className={chatStyles.senderName}>{selectedPartner!.name}</div>}
                    <div className={`${chatStyles.messageBubble} ${isMe ? chatStyles.messageBubbleMe : chatStyles.messageBubbleOther} ${m.status === 'failed' ? chatStyles.messageFailed : ''}`}>{m.content}</div>
                    <div className={`${chatStyles.messageTime} ${isMe ? chatStyles.messageTimeMe : ''}`}>
                      {m.status === 'failed' ? (
                        <button className={chatStyles.retryButton} onClick={() => handleRetry(m)}>Not sent — retry</button>
                      ) : m.status === 'sending' ? 'Sending…' : formatTime(m.createTime)}
                    </div>
                  </div>
                </div>
                )})}
            </div>
            <div className={chatStyles.inputArea}>
              {sendError && <span style={{ color: '#F56C6C', fontSize: 12, marginBottom: 4 }}>{sendError}</span>}
              <input value={input} onChange={e => setInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') handleSend() }} placeholder="Type a message..." />
              <button onClick={handleSend}>Send</button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
