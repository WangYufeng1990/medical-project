import { useEffect, useRef } from 'react'

interface MessageVO {
  id: number
  senderId: number
  receiverId: number
  content: string
  isRead: boolean
  createTime: string
}

export function useChatSse(
  token: string | null,
  currentUserId: number,
  onMessage: (msg: MessageVO) => void
) {
  const onMessageRef = useRef(onMessage)
  onMessageRef.current = onMessage

  useEffect(() => {
    if (!token) return

    let es: EventSource | null = null
    let reconnectTimer: ReturnType<typeof setTimeout>
    let stopped = false

    function connect() {
      if (stopped) return
      es = new EventSource(`/api/v1/chat/subscribe?token=${encodeURIComponent(token!)}`)

      es.addEventListener('new_message', (e: MessageEvent) => {
        try {
          const msg: MessageVO = JSON.parse(e.data)
          onMessageRef.current(msg)
        } catch { /* ignore malformed */ }
      })

      es.addEventListener('connected', () => { /* heartbeat */ })

      es.onerror = () => {
        es?.close()
        if (!stopped) reconnectTimer = setTimeout(connect, 3000)
      }
    }

    connect()

    return () => {
      stopped = true
      clearTimeout(reconnectTimer)
      es?.close()
    }
  }, [token])
}
