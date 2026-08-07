import { useCallback, useEffect, useRef, useState } from 'react'
import { SESSION_WARNING_MINUTES, SESSION_TIMEOUT_MINUTES } from './labels'

// Healthcare-standard idle session: warn before logout, any activity extends the
// session (sliding), so users are never silently kicked after a break.
export function useIdleTimeout(onTimeout: () => void) {
  const [warningVisible, setWarningVisible] = useState(false)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const warnRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  const reset = useCallback(() => {
    setWarningVisible(false)
    if (timeoutRef.current) clearTimeout(timeoutRef.current)
    if (warnRef.current) clearTimeout(warnRef.current)
    warnRef.current = setTimeout(() => setWarningVisible(true), SESSION_WARNING_MINUTES * 60 * 1000)
    timeoutRef.current = setTimeout(onTimeout, SESSION_TIMEOUT_MINUTES * 60 * 1000)
  }, [onTimeout])

  useEffect(() => {
    const events = ['mousemove', 'keydown', 'mousedown', 'touchstart', 'scroll']
    events.forEach(e => window.addEventListener(e, reset))
    reset()
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
      if (warnRef.current) clearTimeout(warnRef.current)
      events.forEach(e => window.removeEventListener(e, reset))
    }
  }, [reset])

  return { warningVisible, reset }
}
