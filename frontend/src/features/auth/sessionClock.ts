/**
 * When to warn about the end of a session, and when to keep it alive.
 *
 * The server ends a session after a period without any request (60 minutes, announced with the user). Typing
 * in a form sends no request, so a person who is working would be signed out. The interface therefore asks
 * "who am I?" from time to time while the person is active, and warns when they have really been idle.
 *
 * Pure functions, so the rules can be tested without a clock or a browser.
 */

/** The warning appears this long before the end (WCAG 2.2.1 asks for at least 20 seconds). */
export const WARN_BEFORE_MS = 120_000

/** An active person keeps the session alive this often. */
export const KEEP_ALIVE_MS = 300_000

export interface ClockSettings {
  timeoutMs: number
  warnMs: number
  keepAliveMs: number
}

/** Short time-outs get proportionally short delays, so the rules still hold. */
export function clockSettings(timeoutSeconds: number): ClockSettings {
  const timeoutMs = timeoutSeconds * 1000
  return {
    timeoutMs,
    warnMs: Math.min(WARN_BEFORE_MS, timeoutMs / 3),
    keepAliveMs: Math.min(KEEP_ALIVE_MS, timeoutMs / 3),
  }
}

export interface ClockInput {
  now: number
  /** The last time the server answered anything: that is when its own clock starts again. */
  lastContact: number
  /** The last time the person touched the keyboard, the mouse or the screen. */
  lastActivity: number
  /** Whether the tab is on screen. A hidden tab does not keep a session alive. */
  visible: boolean
}

export interface ClockReading {
  remainingMs: number
  /** Show the warning. */
  warning: boolean
  /** The server's time-out has passed on our clock: ask it whether the session is really over. */
  expired: boolean
  /** Ask the server "who am I?" to start its clock again. */
  shouldKeepAlive: boolean
}

export function readClock(settings: ClockSettings, input: ClockInput): ClockReading {
  const remainingMs = settings.timeoutMs - (input.now - input.lastContact)
  const expired = remainingMs <= 0
  const recentlyActive = input.now - input.lastActivity < settings.keepAliveMs
  return {
    remainingMs,
    expired,
    warning: !expired && remainingMs <= settings.warnMs,
    shouldKeepAlive:
      !expired && input.visible && recentlyActive && input.now - input.lastContact >= settings.keepAliveMs,
  }
}

/** "1:59", for the countdown. */
export function formatCountdown(remainingMs: number): string {
  const total = Math.max(0, Math.ceil(remainingMs / 1000))
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
