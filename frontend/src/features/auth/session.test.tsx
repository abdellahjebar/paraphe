import { QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { SESSION_ENDED_EVENT } from '@/lib/api/client'
import { createQueryClient } from '@/lib/query'
import { endSession, SESSION_KEY, useSession, useSessionEndedListener } from '@/features/auth/session'

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const USER = {
  id: '5f0f5b7e-1c6e-4f0f-8f3e-0a6f2d7e9c11',
  email: 'khawla@exemple.test',
  displayName: 'Khawla',
  mustChangePassword: false,
  sessionIdleTimeoutSeconds: 3600,
}

function setup() {
  const client = createQueryClient()
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return { client, wrapper }
}

describe('session', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('is loading until the server has answered', () => {
    vi.stubGlobal('fetch', () => new Promise(() => undefined))
    const { wrapper } = setup()

    const { result } = renderHook(() => useSession(), { wrapper })

    expect(result.current.status).toBe('loading')
  })

  it('knows who is signed in and how long the session survives idle', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(200, USER)),
    )
    const { wrapper } = setup()

    const { result } = renderHook(() => useSession(), { wrapper })

    await waitFor(() => expect(result.current.status).toBe('signedIn'))
    expect(result.current).toMatchObject({ user: { displayName: 'Khawla', sessionIdleTimeoutSeconds: 3600 } })
  })

  it('treats "nobody is signed in" as an answer, not as a failure', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(401, { status: 401, code: 'AUTHENTICATION_REQUIRED' })),
    )
    const { wrapper } = setup()

    const { result } = renderHook(() => useSession(), { wrapper })

    await waitFor(() => expect(result.current.status).toBe('signedOut'))
  })

  it('reports a server that failed instead of pretending nobody is signed in', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(500, { status: 500, code: 'INTERNAL_ERROR' })),
    )
    const { wrapper } = setup()

    const { result } = renderHook(() => useSession(), { wrapper })

    await waitFor(() => expect(result.current.status).toBe('error'))
  })

  it('forgets the account and everything it showed when the server ends the session', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(200, USER)),
    )
    const { client, wrapper } = setup()
    client.setQueryData(['projects'], [{ name: 'Chantier confidentiel' }])
    const { result } = renderHook(
      () => {
        useSessionEndedListener()
        return useSession()
      },
      { wrapper },
    )
    await waitFor(() => expect(result.current.status).toBe('signedIn'))

    act(() => {
      window.dispatchEvent(new Event(SESSION_ENDED_EVENT))
    })

    await waitFor(() => expect(result.current.status).toBe('signedOut'))
    expect(client.getQueryData(['projects'])).toBeUndefined()
  })

  it('drops cached data on sign-out and keeps only the "signed out" answer', () => {
    const { client } = setup()
    client.setQueryData(SESSION_KEY, USER)
    client.setQueryData(['projects', 'p1'], { secret: true })

    endSession(client)

    expect(client.getQueryData(SESSION_KEY)).toBeNull()
    expect(client.getQueryData(['projects', 'p1'])).toBeUndefined()
  })
})
