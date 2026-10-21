import { describe, expect, it } from 'vitest'
import { MessageVO } from '../types/entities'
import { applySent, attemptKey, markFailed, mergeHistory, withAttempt } from './chatState'

const STAFF_SELF = { type: 'STAFF' as const, id: 2 }

const attempt = (clientMessageId: string, content = 'hello') => ({
  clientMessageId,
  partnerId: 100,
  partnerType: 'PATIENT' as const,
  content,
})

const stored = (id: number, overrides: Partial<MessageVO> = {}): MessageVO => ({
  id,
  senderId: 2,
  senderType: 'STAFF',
  receiverId: 100,
  receiverType: 'PATIENT',
  content: 'hello',
  isRead: false,
  createTime: '2026-10-21T10:00:00',
  ...overrides,
})

describe('one bubble per attempt', () => {
  it('adds one placeholder for a first send', () => {
    const messages = withAttempt([], STAFF_SELF, attempt('a'))
    expect(messages).toHaveLength(1)
    expect(messages[0].status).toBe('sending')
    expect(attemptKey(messages[0])).toBe('STAFF:2:a')
  })

  it('keeps exactly one bubble across repeated failures and retries', () => {
    let messages = withAttempt([], STAFF_SELF, attempt('a'))
    messages = markFailed(messages, STAFF_SELF, 'a')
    expect(messages).toHaveLength(1)

    for (let i = 0; i < 3; i++) {
      messages = withAttempt(messages, STAFF_SELF, attempt('a'))
      expect(messages).toHaveLength(1)
      messages = markFailed(messages, STAFF_SELF, 'a')
      expect(messages).toHaveLength(1)
      expect(messages[0].status).toBe('failed')
    }
  })

  it('replaces the bubble with the stored message exactly once', () => {
    let messages = withAttempt([], STAFF_SELF, attempt('a'))
    messages = markFailed(messages, STAFF_SELF, 'a')

    messages = withAttempt(messages, STAFF_SELF, attempt('a'))
    messages = applySent(messages, STAFF_SELF, 'a', stored(900), true)

    expect(messages).toHaveLength(1)
    expect(messages[0].id).toBe(900)
    expect(messages[0].status).toBeUndefined()
  })

  it('drops only the local bubble when the answer belongs to another conversation', () => {
    let messages = withAttempt([], STAFF_SELF, attempt('a'))
    messages = applySent(messages, STAFF_SELF, 'a', stored(900), false)

    expect(messages).toHaveLength(0)
  })
})

describe('the key belongs to its sender', () => {
  it('leaves a message from the other party that carries the same client id alone', () => {
    const fromPatient: MessageVO = {
      id: 500, senderId: 100, senderType: 'PATIENT', receiverId: 2, receiverType: 'STAFF',
      content: 'the patient used the same key', isRead: false, createTime: '2026-10-21T09:00:00',
      clientMessageId: 'same-key',
    }
    let messages = withAttempt([fromPatient], STAFF_SELF, attempt('same-key'))
    expect(messages).toHaveLength(2)

    messages = markFailed(messages, STAFF_SELF, 'same-key')
    const kept = messages.find(m => m.id === 500)
    expect(kept?.status).toBeUndefined()
    expect(messages.filter(m => attemptKey(m) === 'PATIENT:100:same-key')).toHaveLength(1)

    messages = applySent(messages, STAFF_SELF, 'same-key', stored(901), true)
    expect(messages.map(m => m.id)).toEqual([500, 901])
  })

  it('keeps both messages when the two sides send under one key', () => {
    const fromPatient: MessageVO = {
      id: 500, senderId: 100, senderType: 'PATIENT', receiverId: 2, receiverType: 'STAFF',
      content: 'patient side', isRead: false, createTime: '2026-10-21T09:00:00',
      clientMessageId: 'shared',
    }
    const merged = mergeHistory([fromPatient], [], new Set())
    expect(merged).toHaveLength(1)

    let messages = withAttempt(merged, STAFF_SELF, attempt('shared'))
    messages = applySent(messages, STAFF_SELF, 'shared', stored(901, { clientMessageId: 'shared' }), true)

    expect(messages.map(m => m.id).sort()).toEqual([500, 901])
  })
})

describe('history pages and local state', () => {
  it('keeps a failed bubble when the history page arrives after the failure', () => {
    let messages = withAttempt([], STAFF_SELF, attempt('a'))
    messages = markFailed(messages, STAFF_SELF, 'a')

    const merged = mergeHistory([stored(800, { content: 'older' })], messages, new Set())

    expect(merged).toHaveLength(2)
    expect(merged[0].id).toBe(800)
    expect(merged[1].status).toBe('failed')
    expect(merged[1].content).toBe('hello')
  })

  it('does not duplicate a message the snapshot already contains', () => {
    let messages = withAttempt([], STAFF_SELF, attempt('a'))
    messages = applySent(messages, STAFF_SELF, 'a', stored(900), true)

    const merged = mergeHistory([stored(900, { clientMessageId: 'a' })], messages, new Set([900]))

    expect(merged).toHaveLength(1)
    expect(merged[0].id).toBe(900)
  })

  it('keeps a just-sent message that a stale snapshot does not contain', () => {
    const confirmed = new Set<number>([900])
    const justSent = stored(900)

    const merged = mergeHistory([stored(800)], [justSent], confirmed)

    expect(merged.map(m => m.id)).toEqual([800, 900])
    expect(confirmed.has(900)).toBe(true)
  })

  it('forgets a confirmed message once a snapshot carries it', () => {
    const confirmed = new Set<number>([900])
    const merged = mergeHistory([stored(900)], [stored(900)], confirmed)

    expect(merged.map(m => m.id)).toEqual([900])
    expect(confirmed.has(900)).toBe(false)
  })
})
