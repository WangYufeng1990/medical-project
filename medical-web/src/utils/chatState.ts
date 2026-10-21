import type { ChatMessage, MessageVO } from '../types/entities'
import type { PartyType } from '../api/chat'

/**
 * Sending a message keeps a local copy until the server answers: an optimistic bubble
 * that becomes the stored message on success, or a failed bubble that a retry re-sends
 * under the same client message id (Round 54.3).
 * <p>
 * These helpers are pure so the state rules — which bubble a retry reuses, what an
 * arriving history page may replace, whose identical key is whose — can be tested
 * without a browser.
 */

export interface Attempt {
  clientMessageId: string
  partnerId: number
  partnerType: PartyType
  content: string
}

export interface Self {
  type: PartyType
  id: number
}

/** One send attempt's identity. The server scopes the key by sender, and so do we. */
export const attemptKey = (m: Pick<ChatMessage, 'senderType' | 'senderId' | 'clientMessageId'>) =>
  m.clientMessageId ? `${m.senderType}:${m.senderId}:${m.clientMessageId}` : ''

const keyFor = (self: Self, clientMessageId: string) => `${self.type}:${self.id}:${clientMessageId}`

/**
 * Shows the attempt as sending. A first send adds the bubble; a retry finds the bubble
 * it already owns and only flips its status, so repeated failures never stack up.
 */
export function withAttempt(messages: ChatMessage[], self: Self, attempt: Attempt): ChatMessage[] {
  const key = keyFor(self, attempt.clientMessageId)
  let found = false
  const updated = messages.map(m => {
    if (attemptKey(m) !== key) return m
    found = true
    return { ...m, status: 'sending' as const }
  })
  if (found) return updated
  return [...messages, {
    id: -Date.now(),
    clientMessageId: attempt.clientMessageId,
    status: 'sending',
    senderId: self.id,
    senderType: self.type,
    receiverId: attempt.partnerId,
    receiverType: attempt.partnerType,
    content: attempt.content,
    isRead: false,
    createTime: new Date().toISOString(),
  }]
}

/**
 * Marks this attempt as failed. Only a bubble this client created is touched: a message
 * from the other party may legitimately carry the same client message id, and it must
 * neither be marked nor removed (the server allows one key per sender).
 */
export function markFailed(messages: ChatMessage[], self: Self, clientMessageId: string): ChatMessage[] {
  const key = keyFor(self, clientMessageId)
  return messages.map(m => attemptKey(m) === key && m.status ? { ...m, status: 'failed' as const } : m)
}

/**
 * Replaces the attempt's bubble with the message the server stored. When the response
 * belongs to a conversation the user has since left, only the local bubble is dropped:
 * the message must not be appended to the thread on screen.
 */
export function applySent(messages: ChatMessage[], self: Self, clientMessageId: string,
                          sent: MessageVO, belongsHere: boolean): ChatMessage[] {
  const key = keyFor(self, clientMessageId)
  const withoutAttempt = messages.filter(m => attemptKey(m) !== key)
  return belongsHere ? [...withoutAttempt, { ...sent }] : withoutAttempt
}

/**
 * Page one of a thread: the server's snapshot, plus the local bubbles it cannot know
 * about yet — attempts that are still sending or failed, and a message this client has
 * just sent that a snapshot taken before the send does not contain.
 * <p>
 * `confirmedIds` is the local record of "sent and acknowledged this session": an id the
 * snapshot does have is dropped (and forgotten), an id it lacks is kept, so an older
 * snapshot arriving late cannot wipe a message the user already saw go through.
 */
/** What the open thread currently holds: its messages, the page they represent, and the total. */
export interface ThreadPanel {
  messages: ChatMessage[]
  page: number
  total: number
}

export const emptyPanel = (): ThreadPanel => ({ messages: [], page: 0, total: 0 })

/**
 * The panel after a history page arrives. The page the panel reports is the page it
 * actually holds: when a send refreshes page one, that refresh *replaces* the older
 * pages, so the page counter must go back to one — otherwise "load earlier" asks for
 * page three next and page two is never loaded (Round 54.3 review).
 */
export function panelAfterPage(panel: ThreadPanel, batch: MessageVO[], requestedPage: number,
                               total: number, confirmedIds: Set<number>): ThreadPanel {
  const messages = requestedPage === 1
    ? mergeHistory(batch, panel.messages, confirmedIds)
    : [...batch, ...panel.messages]
  return { messages, page: requestedPage, total }
}

/** The page "load earlier" asks for next: always one after what the panel holds. */
export const nextPage = (panel: ThreadPanel) => panel.page + 1

export function mergeHistory(batch: MessageVO[], previous: ChatMessage[],
                             confirmedIds: Set<number>): ChatMessage[] {
  const snapshotIds = new Set(batch.map(m => m.id))
  const snapshotKeys = new Set(batch.map(attemptKey).filter(Boolean))

  const keep = previous.filter(m => {
    if (m.id > 0 && snapshotIds.has(m.id)) {
      confirmedIds.delete(m.id)
      return false
    }
    if (m.status) return !snapshotKeys.has(attemptKey(m))
    return confirmedIds.has(m.id)
  })

  return [...batch, ...keep]
}
