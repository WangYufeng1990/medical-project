import { useEffect, useRef } from 'react'
import { MessageVO } from '../types/entities'

export function useChatSse(
  getTicket: () => Promise<string>,
  currentUserId: number,
  onMessage: (msg: MessageVO) => void
) {
  const onMessageRef = useRef(onMessage)
  onMessageRef.current = onMessage
  const getTicketRef = useRef(getTicket)
  getTicketRef.current = getTicket

  useEffect(() => {
    let es: EventSource | null = null
    let reconnectTimer: ReturnType<typeof setTimeout>
    let stopped = false

    async function connect() {
      if (stopped) return
      // Tickets are single-use, so fetch a fresh one per connection attempt.
      const ticket = await getTicketRef.current().catch(() => null)
      if (!ticket) {
        // Session expired or server down — SSE is best-effort, stop retrying.
        stopped = true
        return
      }
      if (stopped) return
      es = new EventSource(`/api/v1/chat/subscribe?ticket=${encodeURIComponent(ticket)}`)

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
  }, [])
}
