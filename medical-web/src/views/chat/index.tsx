import { useState, useEffect, useRef, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getConversations, getMessages, sendMessage, getSseTicket, PartyType } from '../../api/chat'
import { useChatSse } from '../../hooks/useChatSse'
import { parseJwt } from '../../utils/auth'
import { tokenStore } from '../../utils/auth'
import { MessageVO, ConversationVO } from '../../types/entities'
import styles from './style.module.css'

export default function Chat() {
  const [searchParams] = useSearchParams()
  const [conversations, setConversations] = useState<ConversationVO[]>([])
  const [selectedPartner, setSelectedPartner] = useState<{ id: number; name: string; type: PartyType } | null>(null)
  const [messages, setMessages] = useState<MessageVO[]>([])
  const [msgPage, setMsgPage] = useState(1)
  const [msgTotal, setMsgTotal] = useState(0)
  const [input, setInput] = useState('')
  const [sendError, setSendError] = useState('')
  const messageListRef = useRef<HTMLDivElement>(null)

  const token = tokenStore.get('token')
  const raw = token ? parseJwt(token) : {}
  const currentUserId = Number(raw.uid ?? raw.jti ?? 0)

  const loadConversations = useCallback(() => {
    getConversations(1, 20).then(r => setConversations(r.records ?? []))
  }, [])

  useEffect(() => { loadConversations() }, [loadConversations])

  // Auto-select partner from URL query params (e.g., /chat?partnerId=100&partnerName=James+Anderson&partnerType=PATIENT)
  useEffect(() => {
    const pid = searchParams.get('partnerId')
    const pname = searchParams.get('partnerName')
    if (pid && pname) {
      const ptype = searchParams.get('partnerType') === 'STAFF' ? 'STAFF' : 'PATIENT'
      setSelectedPartner({ id: Number(pid), name: pname, type: ptype })
      loadMessages(Number(pid), ptype, 1)
    }
  }, [searchParams])

  const loadMessages = useCallback((partnerId: number, partnerType: PartyType, page: number) => {
    return getMessages(partnerId, partnerType, page, 50).then(r => {
      const batch = (r.records ?? []).reverse() // API returns DESC, reverse to ASC for display
      setMessages(prev => page === 1 ? batch : [...batch, ...prev])
      setMsgTotal(r.total ?? 0)
    })
  }, [])

  const selectPartner = (p: ConversationVO) => {
    setSelectedPartner({ id: p.partnerId, name: p.partnerName, type: p.partnerType })
    setMsgPage(1)
    setMessages([])
    loadMessages(p.partnerId, p.partnerType, 1).then(() => loadConversations())
  }

  const handleSend = async () => {
    if (!input.trim() || !selectedPartner) return
    const content = input.trim()
    setInput('')
    const msg: MessageVO = { id: Date.now(), senderId: currentUserId, senderType: 'STAFF', receiverId: selectedPartner.id, receiverType: selectedPartner.type, content, isRead: false, createTime: new Date().toISOString() }
    setMessages(prev => [...prev, msg])
    setSendError('')
    await sendMessage(selectedPartner.id, selectedPartner.type, content).catch(() => {
      setMessages(prev => prev.filter(m => m.id !== msg.id))
      setSendError('Message could not be sent. Please try again.')
    })
    setTimeout(() => {
      messageListRef.current?.scrollTo({ top: messageListRef.current.scrollHeight, behavior: 'smooth' })
    }, 50)
  }

  const handleSseMessage = useCallback((msg: MessageVO) => {
    if (selectedPartner && (
      (msg.senderType === selectedPartner.type && msg.senderId === selectedPartner.id) ||
      (msg.receiverType === selectedPartner.type && msg.receiverId === selectedPartner.id))) {
      setMessages(prev => { if (prev.find(m => m.id === msg.id)) return prev; return [...prev, msg] })
      setTimeout(() => {
        messageListRef.current?.scrollTo({ top: messageListRef.current.scrollHeight, behavior: 'smooth' })
      }, 50)
    }
    loadConversations()
  }, [selectedPartner, loadConversations])

  useChatSse(() => getSseTicket().then(r => r.ticket), currentUserId, handleSseMessage)

  useEffect(() => {
    if (messageListRef.current) messageListRef.current.scrollTop = messageListRef.current.scrollHeight
  }, [messages.length])

  const hasMore = messages.length < msgTotal

  const formatTime = (t: string) => {
    if (!t) return ''
    const d = new Date(t)
    const now = new Date()
    const isToday = d.toDateString() === now.toDateString()
    return isToday ? d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : d.toLocaleDateString()
  }

  return (
    <div className={styles.wrapper}>
      <div className={styles.sidebar}>
        <div className={styles.sidebarHeader}>Messages</div>
        <div className={styles.conversationList}>
          {conversations.map(c => (
            <div key={`${c.partnerType}:${c.partnerId}`}
              className={`${styles.conversationItem} ${selectedPartner?.id === c.partnerId && selectedPartner?.type === c.partnerType ? styles.conversationItemActive : ''}`}
              onClick={() => selectPartner(c)}>
              <div className={styles.partnerName}>{c.partnerName}</div>
              <div className={styles.lastMsg}>{c.lastMessage}</div>
              <div className={styles.conversationMeta}>
                <span className={styles.conversationTime}>{formatTime(c.lastMessageTime || '')}</span>
                {(c.unreadCount ?? 0) > 0 && <span className={styles.unreadBadge}>{c.unreadCount}</span>}
              </div>
            </div>
          ))}
          {conversations.length === 0 && <div style={{ padding: 24, textAlign: 'center', color: '#c0c4cc', fontSize: 13 }}>No conversations</div>}
        </div>
      </div>
      <div className={styles.chatArea}>
        {!selectedPartner ? (
          <div className={styles.chatPlaceholder}>Select a conversation</div>
        ) : (
          <>
            <div className={styles.chatHeader}>{selectedPartner.name}</div>
            <div className={styles.messageList} ref={messageListRef}>
              {hasMore && (
                <div className={styles.loadMore}>
                  <button onClick={() => { const np = msgPage + 1; setMsgPage(np); loadMessages(selectedPartner.id, selectedPartner.type, np) }}>Load earlier</button>
                </div>
              )}
              {messages.map(m => {
                const isMe = m.senderType === 'STAFF' && m.senderId === currentUserId
                return (
                <div key={m.id} className={`${styles.messageRow} ${isMe ? styles.messageRowMe : styles.messageRowOther}`}>
                  <div>
                    {!isMe && <div className={styles.senderName}>{selectedPartner!.name}</div>}
                    <div className={`${styles.messageBubble} ${isMe ? styles.messageBubbleMe : styles.messageBubbleOther}`}>{m.content}</div>
                    <div className={`${styles.messageTime} ${isMe ? styles.messageTimeMe : ''}`}>{formatTime(m.createTime)}</div>
                  </div>
                </div>
                )})}
            </div>
            <div className={styles.inputArea}>
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
