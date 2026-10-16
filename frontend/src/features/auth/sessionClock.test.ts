import {
  clockSettings,
  formatCountdown,
  KEEP_ALIVE_MS,
  readClock,
  WARN_BEFORE_MS,
  type ClockInput,
} from '@/features/auth/sessionClock'

const HOUR = 3600
const settings = clockSettings(HOUR)
const MIN = 60_000

function reading(overrides: Partial<ClockInput>) {
  return readClock(settings, { now: 0, lastContact: 0, lastActivity: 0, visible: true, ...overrides })
}

describe('session clock', () => {
  it('uses the delays of the design: warning two minutes before the end, keep-alive every five minutes', () => {
    expect(settings).toEqual({ timeoutMs: 60 * MIN, warnMs: WARN_BEFORE_MS, keepAliveMs: KEEP_ALIVE_MS })
    expect(WARN_BEFORE_MS).toBe(2 * MIN)
    expect(KEEP_ALIVE_MS).toBe(5 * MIN)
  })

  it('gives at least 20 seconds to react, which WCAG 2.2.1 asks for', () => {
    expect(WARN_BEFORE_MS).toBeGreaterThanOrEqual(20_000)
  })

  it('has nothing to say while the session is young', () => {
    const r = reading({ now: 10 * MIN, lastContact: 8 * MIN, lastActivity: 0 })
    expect(r).toMatchObject({ warning: false, expired: false, shouldKeepAlive: false })
  })

  it('warns exactly when two minutes remain, and not a second sooner', () => {
    expect(reading({ now: 58 * MIN - 1, lastActivity: -60 * MIN }).warning).toBe(false)
    expect(reading({ now: 58 * MIN, lastActivity: -60 * MIN }).warning).toBe(true)
    expect(reading({ now: 59 * MIN, lastActivity: -60 * MIN }).remainingMs).toBe(MIN)
  })

  it('is expired when the time-out has passed, and then only asks the server', () => {
    const r = reading({ now: 60 * MIN, lastActivity: 60 * MIN })
    expect(r).toMatchObject({ expired: true, warning: false, shouldKeepAlive: false })
  })

  it('keeps the session alive for a person who is active, once the last contact is five minutes old', () => {
    expect(reading({ now: 5 * MIN - 1, lastActivity: 5 * MIN - 1 }).shouldKeepAlive).toBe(false)
    expect(reading({ now: 5 * MIN, lastActivity: 5 * MIN - 1 }).shouldKeepAlive).toBe(true)
  })

  it('does not keep a session alive for someone who has been idle', () => {
    expect(reading({ now: 20 * MIN, lastContact: 0, lastActivity: 5 * MIN }).shouldKeepAlive).toBe(false)
  })

  it('does not keep a session alive from a tab nobody can see', () => {
    expect(reading({ now: 6 * MIN, lastActivity: 6 * MIN, visible: false }).shouldKeepAlive).toBe(false)
  })

  it('treats a request made by anyone as the start of a new period', () => {
    // contact at minute 50 means the end is at minute 110, so there is no warning at minute 58
    expect(reading({ now: 58 * MIN, lastContact: 50 * MIN }).warning).toBe(false)
  })

  it('scales its delays for a short time-out, so the rules still hold', () => {
    const short = clockSettings(180)
    expect(short.warnMs).toBe(60_000)
    expect(short.keepAliveMs).toBe(60_000)
    expect(short.warnMs).toBeGreaterThanOrEqual(20_000)
  })

  it('writes the countdown as minutes and seconds', () => {
    expect(formatCountdown(119_000)).toBe('1:59')
    expect(formatCountdown(120_000)).toBe('2:00')
    expect(formatCountdown(61_001)).toBe('1:02')
    expect(formatCountdown(500)).toBe('0:01')
    expect(formatCountdown(-5)).toBe('0:00')
  })
})
