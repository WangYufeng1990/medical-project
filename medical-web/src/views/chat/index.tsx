import { useState, useEffect, useRef, useCallback } from 'react'
import { getConversations, getMessages, sendMessage } from '../../api/chat'
import { useChatSse } from '../../hooks/useChatSse'
import { parseJwt } from '../../utils/auth'
import styles from './style.module.css'

interface MessageVO { id: number; senderId: number; receiverId: number; content: string; isRead: boolean; createTime: string }
interface ConversationVO { partnerId: number; partnerName: string; lastMessage: string; lastMessageTime: string; unreadCount: number }

export default function Chat() {
  const [conversations, setConversations] = useState<ConversationVO[]>([])
  const [selectedPartner, setSelectedPartner] = useState<{ id: number; name: string } | null>(null)
  const [messages, setMessages] = useState<MessageVO[]>([])
  const [msgPage, setMsgPage] = useState(1)
  const [msgTotal, setMsgTotal] = useState(0)
  const [input, setInput] = useState('')
  const messageListRef = useRef<HTMLDivElement>(null)

  const token = localStorage.getItem('token')
  const raw = token ? parseJwt(token) ?? {} : {}
  const currentUserId: number = (raw as any).uid ?? (raw as any).jti ?? 0

  const loadConversations = useCallback(() => {
    getConversations(1, 20).then((r: any) => setConversations(r.records ?? []))
  }, [])

  useEffect(() => { loadConversations() }, [loadConversations])

  const loadMessages = useCallback((partnerId: number, page: number) => {
    getMessages(partnerId, page, 50).then((r: any) => {
      const batch = r.records ?? []
      setMessages(prev => page === 1 ? batch : [...batch, ...prev])
      setMsgTotal(r.total ?? 0)
    })
  }, [])

  const selectPartner = (p: { partnerId: number; partnerName: string }) => {
    setSelectedPartner({ id: p.partnerId, name: p.partnerName })
    setMsgPage(1)
    setMessages([])
    loadMessages(p.partnerId, 1)
    loadConversations()
  }

  const handleSend = async () => {
    if (!input.trim() || !selectedPartner) return
    const content = input.trim()
    setInput('')
    const msg = { id: Date.now(), senderId: currentUserId, receiverId: selectedPartner.id, content, isRead: false, createTime: new Date().toISOString() }
    setMessages(prev => [...prev, msg])
    await sendMessage(selectedPartner.id, content).catch(() => setMessages(prev => prev.filter(m => m.id !== msg.id)))
    setTimeout(() => {
      messageListRef.current?.scrollTo({ top: messageListRef.current.scrollHeight, behavior: 'smooth' })
    }, 50)
  }

  const handleSseMessage = useCallback((msg: MessageVO) => {
    if (selectedPartner && (msg.senderId === selectedPartner.id || msg.receiverId === selectedPartner.id)) {
      setMessages(prev => { if (prev.find(m => m.id === msg.id)) return prev; return [...prev, msg] })
      setTimeout(() => {
        messageListRef.current?.scrollTo({ top: messageListRef.current.scrollHeight, behavior: 'smooth' })
      }, 50)
    }
    loadConversations()
  }, [selectedPartner, loadConversations])

  useChatSse(token, currentUserId, handleSseMessage)

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
            <div key={c.partnerId}
              className={`${styles.conversationItem} ${selectedPartner?.id === c.partnerId ? styles.conversationItemActive : ''}`}
              onClick={() => selectPartner(c)}>
              <div className={styles.partnerName}>{c.partnerName}</div>
              <div className={styles.lastMsg}>{c.lastMessage}</div>
              <div className={styles.conversationMeta}>
                <span className={styles.conversationTime}>{formatTime(c.lastMessageTime)}</span>
                {c.unreadCount > 0 && <span className={styles.unreadBadge}>{c.unreadCount}</span>}
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
                  <button onClick={() => { const np = msgPage + 1; setMsgPage(np); loadMessages(selectedPartner.id, np) }}>Load earlier</button>
                </div>
              )}
              {messages.map(m => {
                const isMe = m.senderId === currentUserId
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
              <input value={input} onChange={e => setInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') handleSend() }} placeholder="Type a message..." />
              <button onClick={handleSend}>Send</button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
