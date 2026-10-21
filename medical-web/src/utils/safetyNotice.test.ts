import { describe, expect, it } from 'vitest'
import { displayedSafetyNotice, FALLBACK_SAFETY_NOTICE } from './safetyNotice'

const SERVER_NOTICE = {
  version: 1,
  emergencyNotice: 'server emergency wording',
  responseNotice: 'server response wording',
}

describe('the safety notice is always on screen', () => {
  it('shows the fixed notice while the request is still in flight', () => {
    const notice = displayedSafetyNotice(null)

    expect(notice).toBe(FALLBACK_SAFETY_NOTICE)
    expect(notice.emergencyNotice).toContain('911')
    expect(notice.responseNotice).toContain('may take time')
  })

  it('keeps the fixed notice when the request fails', () => {
    // The component renders this on a failed fetch: null in, fallback out.
    let notice = displayedSafetyNotice(null)
    notice = displayedSafetyNotice(undefined)

    expect(notice.version).toBe(0)
    expect(notice.emergencyNotice).toContain('911')
    expect(notice.responseNotice).not.toContain('24/7')
  })

  it('uses the server\'s reviewed wording once it arrives', () => {
    const notice = displayedSafetyNotice(SERVER_NOTICE)

    expect(notice.version).toBe(1)
    expect(notice.emergencyNotice).toBe('server emergency wording')
    expect(notice.responseNotice).toBe('server response wording')
  })
})
