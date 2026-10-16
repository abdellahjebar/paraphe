import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import { CSRF_COOKIE, SESSION_ENDED_EVENT } from '@/lib/api/client'
import { createQueryClient } from '@/lib/query'
import {
  SESSION_KEY,
  setLoginReason,
  useLoginReason,
  useSession,
  useSessionEndedListener,
} from '@/features/auth/session'
import { SessionWatcher } from '@/features/auth/SessionWatcher'
import type { SessionMessage } from '@/features/auth/sessionChannel'

// The other tabs are replaced by a hand-made channel: the test decides what they say.
const channel = vi.hoisted(() => ({
  listener: undefined as undefined | ((m: SessionMessage) => void),
  sent: [] as unknown[],
}))
vi.mock('@/features/auth/sessionChannel', () => ({
  broadcastSession: (message: unknown) => channel.sent.push(message),
  listenToSession: (listener: (m: SessionMessage) => void) => {
    channel.listener = listener
    return () => {
      channel.listener = undefined
    }
  },
}))

const MIN = 60_000
const USER = {
  id: '5f0f5b7e-1c6e-4f0f-8f3e-0a6f2d7e9c11',
  email: 'khawla@exemple.test',
  displayName: 'Khawla',
  mustChangePassword: false,
  sessionIdleTimeoutSeconds: 3600,
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function server(me: () => Response) {
  const asked: number[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => {
      asked.push(Date.now())
      document.cookie = `${CSRF_COOKIE}=token; path=/`
      return me()
    }),
  )
  return asked
}

function Reason() {
  const reason = useLoginReason()
  return <p data-testid="reason">{reason ?? 'none'}</p>
}

function Harness() {
  // The route guard observes the session in the real application; without an observer the cache would be emptied.
  useSession()
  useSessionEndedListener()
  return (
    <>
      <SessionWatcher timeoutSeconds={3600} />
      <Reason />
    </>
  )
}

function setup() {
  const client = createQueryClient()
  client.setQueryData(SESSION_KEY, USER)
  render(
    <QueryClientProvider client={client}>
      <Harness />
    </QueryClientProvider>,
  )
  return { client }
}

/** Moves the clock and lets the timers, the promises and the rendering that follow run. */
async function pass(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
}

describe('SessionWatcher', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setLoginReason(undefined)
    channel.sent.length = 0
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('says nothing to someone who is working: it keeps the session alive instead', async () => {
    const asked = server(() => json(200, USER))
    setup()

    // someone typing and moving the mouse for 30 minutes
    for (let minute = 0; minute < 30; minute += 1) {
      act(() => {
        window.dispatchEvent(new Event('keydown'))
      })
      await pass(MIN)
    }

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    // about every five minutes, never more often
    expect(asked.length).toBeGreaterThanOrEqual(5)
    expect(asked.length).toBeLessThanOrEqual(7)
  })

  it('warns someone who has been idle, with the time left, two minutes before the end', async () => {
    server(() => json(200, USER))
    setup()

    await pass(57 * MIN)
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()

    await pass(MIN + 1000)
    const dialog = screen.getByRole('alertdialog')
    expect(dialog).toHaveAccessibleName('Votre session va se terminer')
    expect(dialog).toHaveTextContent('Elle se terminera dans')
    expect(screen.getByRole('timer')).toHaveTextContent(/^1:5\d$/)
    // the dialog moves the focus on the next animation frame
    await pass(100)
    expect(screen.getByRole('button', { name: 'Rester connecté' })).toHaveFocus()
  })

  it('counts down in the dialog', async () => {
    server(() => json(200, USER))
    setup()
    await pass(58 * MIN + 1000)
    const before = screen.getByRole('timer').textContent

    await pass(30_000)

    expect(screen.getByRole('timer').textContent).not.toBe(before)
  })

  it('starts the server clock again, and closes the dialog, when the person says they are there', async () => {
    const asked = server(() => json(200, USER))
    const { client } = setup()
    await pass(58 * MIN + 1000)
    screen.getByRole('alertdialog')
    const before = asked.length

    await act(async () => {
      screen.getByRole('button', { name: 'Rester connecté' }).click()
      await vi.advanceTimersByTimeAsync(1500)
    })

    expect(asked.length).toBe(before + 1)
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(client.getQueryData(SESSION_KEY)).toEqual(USER)
  })

  it('takes the Escape key as "I am here", the safe answer', async () => {
    const asked = server(() => json(200, USER))
    setup()
    await pass(58 * MIN + 1000)
    screen.getByRole('alertdialog')
    const before = asked.length

    await act(async () => {
      window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await vi.advanceTimersByTimeAsync(1500)
    })

    expect(asked.length).toBeGreaterThan(before)
  })

  it('ends the session on the interface when the server says it is over', async () => {
    server(() => json(401, { status: 401, code: 'AUTHENTICATION_REQUIRED' }))
    const { client } = setup()
    client.setQueryData(['projects'], [{ name: 'Chantier confidentiel' }])

    await pass(61 * MIN)

    expect(client.getQueryData(SESSION_KEY)).toBeNull()
    expect(client.getQueryData(['projects'])).toBeUndefined()
    expect(screen.getByTestId('reason')).toHaveTextContent('ended')
    expect(channel.sent).toContainEqual({ type: 'ended', reason: 'ended' })
  })

  it('carries on when the server says the session is still alive after the expected time', async () => {
    server(() => json(200, USER))
    const { client } = setup()

    await pass(61 * MIN)

    expect(client.getQueryData(SESSION_KEY)).toEqual(USER)
    expect(screen.getByTestId('reason')).toHaveTextContent('none')
  })

  it('does not keep the session alive from a tab that is hidden', async () => {
    const asked = server(() => json(200, USER))
    setup()
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'))
    })

    for (let minute = 0; minute < 10; minute += 1) {
      act(() => {
        window.dispatchEvent(new Event('keydown'))
      })
      await pass(MIN)
    }

    expect(asked).toHaveLength(0)
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })

  it('does not warn when another tab has kept the session alive', async () => {
    server(() => json(200, USER))
    setup()
    await pass(50 * MIN)

    act(() => channel.listener?.({ type: 'contact', at: Date.now() }))
    await pass(10 * MIN)

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('follows another tab that signed out', async () => {
    server(() => json(200, USER))
    const { client } = setup()

    act(() => channel.listener?.({ type: 'ended', reason: 'signedOut' }))

    expect(client.getQueryData(SESSION_KEY)).toBeNull()
    expect(screen.getByTestId('reason')).toHaveTextContent('signedOut')
  })

  it('tells the other tabs when the server ended the session', async () => {
    server(() => json(200, USER))
    setup()

    act(() => {
      window.dispatchEvent(new Event(SESSION_ENDED_EVENT))
    })

    expect(channel.sent).toContainEqual({ type: 'ended', reason: 'ended' })
  })
})
