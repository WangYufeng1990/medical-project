import { useEffect, useRef } from 'react'

const IDLE_MINUTES = 30

export function useIdleTimeout(onTimeout: () => void) {
  const timerRef = useRef<ReturnType<typeof setTimeout>>()

  useEffect(() => {
    const reset = () => {
      if (timerRef.current) clearTimeout(timerRef.current)
      timerRef.current = setTimeout(onTimeout, IDLE_MINUTES * 60 * 1000)
    }
    const events = ['mousemove', 'keydown', 'mousedown', 'touchstart', 'scroll']
    events.forEach(e => window.addEventListener(e, reset))
    reset()
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
      events.forEach(e => window.removeEventListener(e, reset))
    }
  }, [onTimeout])
}
