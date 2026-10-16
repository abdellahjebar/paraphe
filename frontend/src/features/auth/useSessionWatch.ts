import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { API_CONTACT_EVENT, api, SESSION_ENDED_EVENT } from '@/lib/api/client'
import { isApiError } from '@/lib/api/errors'
import { endSession, setLoginReason } from '@/features/auth/session'
import { broadcastSession, listenToSession } from '@/features/auth/sessionChannel'
import { clockSettings, readClock } from '@/features/auth/sessionClock'

const ACTIVITY_EVENTS = ['keydown', 'pointerdown', 'pointermove', 'scroll', 'touchstart'] as const
const TICK_MS = 1000
/** After a keep-alive that failed to reach the server, wait before trying again. */
const RETRY_AFTER_FAILURE_MS = 30_000

export interface SessionWatch {
  /** The warning is showing. */
  warning: boolean
  remainingMs: number
  /** Start the server's clock again: the person says they are still there. */
  stayConnected: () => void
}

/**
 * Watches the session of a signed-in person (WCAG 2.2.1): keeps it alive while they are active, warns when
 * they have been idle until shortly before the end, and notices when the server has ended it.
 *
 * `timeoutSeconds` is what the server announced with the user. Zero or less means "unknown": nothing is done.
 */
export function useSessionWatch(timeoutSeconds: number): SessionWatch {
  const client = useQueryClient()
  const settings = useMemo(() => clockSettings(timeoutSeconds), [timeoutSeconds])
  const enabled = timeoutSeconds > 0
  const [reading, setReading] = useState({ warning: false, remainingMs: settings.timeoutMs })

  // Both start at the moment the watch starts (set in the effect below), not during rendering.
  const lastContact = useRef(0)
  const lastActivity = useRef(0)
  const asking = useRef(false)
  const nextTryAt = useRef(0)

  /** Asks the server who is signed in. Whatever it answers, that is a request, so its clock starts again. */
  const askServer = useCallback(async () => {
    if (asking.current || Date.now() < nextTryAt.current) return
    asking.current = true
    try {
      await api.GET('/api/auth/me')
    } catch (error) {
      if (isApiError(error) && error.code === 'AUTHENTICATION_REQUIRED') {
        // "Who am I?" is allowed to be answered with 401 by the client, but here it means the session is over.
        window.dispatchEvent(new Event(SESSION_ENDED_EVENT))
      } else {
        nextTryAt.current = Date.now() + RETRY_AFTER_FAILURE_MS
      }
    } finally {
      asking.current = false
    }
  }, [])

  useEffect(() => {
    if (!enabled) return
    lastContact.current = Date.now()
    lastActivity.current = Date.now()

    const tick = () => {
      const result = readClock(settings, {
        now: Date.now(),
        lastContact: lastContact.current,
        lastActivity: lastActivity.current,
        visible: document.visibilityState !== 'hidden',
      })
      setReading((previous) =>
        previous.warning === result.warning && !result.warning
          ? previous
          : { warning: result.warning, remainingMs: result.remainingMs },
      )
      // Active people keep the session alive; when the time is up, the server is asked whether it really is.
      if (result.shouldKeepAlive || result.expired) void askServer()
    }

    const onContact = (event: Event) => {
      const at = (event as CustomEvent<number>).detail
      lastContact.current = Math.max(lastContact.current, at)
      broadcastSession({ type: 'contact', at })
      tick()
    }
    const onActivity = () => {
      lastActivity.current = Date.now()
    }
    const onEnded = () => broadcastSession({ type: 'ended', reason: 'ended' })

    window.addEventListener(API_CONTACT_EVENT, onContact)
    window.addEventListener(SESSION_ENDED_EVENT, onEnded)
    document.addEventListener('visibilitychange', tick)
    for (const name of ACTIVITY_EVENTS) window.addEventListener(name, onActivity, { passive: true })
    const stopListening = listenToSession((message) => {
      if (message.type === 'contact') {
        lastContact.current = Math.max(lastContact.current, message.at)
        tick()
      } else {
        setLoginReason(message.reason)
        endSession(client)
      }
    })
    const timer = window.setInterval(tick, TICK_MS)
    tick()

    return () => {
      window.clearInterval(timer)
      stopListening()
      window.removeEventListener(API_CONTACT_EVENT, onContact)
      window.removeEventListener(SESSION_ENDED_EVENT, onEnded)
      document.removeEventListener('visibilitychange', tick)
      for (const name of ACTIVITY_EVENTS) window.removeEventListener(name, onActivity)
    }
  }, [enabled, settings, askServer, client])

  const stayConnected = useCallback(() => {
    lastActivity.current = Date.now()
    nextTryAt.current = 0
    void askServer()
  }, [askServer])

  return { warning: enabled && reading.warning, remainingMs: reading.remainingMs, stayConnected }
}
