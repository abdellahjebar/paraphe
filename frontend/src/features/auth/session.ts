import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useEffect, useSyncExternalStore } from 'react'
import { api, SESSION_ENDED_EVENT } from '@/lib/api/client'
import { isApiError } from '@/lib/api/errors'
import type { components } from '@/lib/api/schema'

export type SessionUser = components['schemas']['UserResponse']

export const SESSION_KEY = ['session'] as const

/**
 * Who is signed in, or null when nobody is. A refusal because nobody is signed in is an answer, not a
 * failure; any other failure is thrown so the screen can say the server could not be asked.
 */
export async function fetchSession(): Promise<SessionUser | null> {
  try {
    const { data } = await api.GET('/api/auth/me')
    return data ?? null
  } catch (error) {
    if (isApiError(error) && error.code === 'AUTHENTICATION_REQUIRED') return null
    throw error
  }
}

export type SessionState =
  | { status: 'loading' }
  | { status: 'error'; error: unknown; retry: () => void }
  | { status: 'signedOut' }
  | { status: 'signedIn'; user: SessionUser }

export function useSession(): SessionState {
  const query = useQuery({
    queryKey: SESSION_KEY,
    queryFn: fetchSession,
    // The session changes when the person signs in or out, or when the server ends it: never by waiting.
    staleTime: Infinity,
    gcTime: Infinity,
  })
  if (query.isPending) return { status: 'loading' }
  if (query.isError) return { status: 'error', error: query.error, retry: () => void query.refetch() }
  return query.data ? { status: 'signedIn', user: query.data } : { status: 'signedOut' }
}

/**
 * Everything the signed-in person could see is dropped, so that after signing out (or after the server ended
 * the session) nothing of their account stays in memory.
 */
export function endSession(client: QueryClient): void {
  client.removeQueries({ predicate: (query) => query.queryKey[0] !== SESSION_KEY[0] })
  client.setQueryData(SESSION_KEY, null)
}

export type LoginReason = 'ended' | 'signedOut'

const reasonStore: { value: LoginReason | undefined; listeners: Set<() => void> } = {
  value: undefined,
  listeners: new Set(),
}

/** Remembers why the person is no longer signed in, so the sign-in page can say so. Cleared by signing in. */
export function setLoginReason(reason: LoginReason | undefined): void {
  reasonStore.value = reason
  reasonStore.listeners.forEach((listener) => listener())
}

function subscribeToReason(listener: () => void) {
  reasonStore.listeners.add(listener)
  return () => reasonStore.listeners.delete(listener)
}

export function useLoginReason(): LoginReason | undefined {
  return useSyncExternalStore(
    subscribeToReason,
    () => reasonStore.value,
    () => undefined,
  )
}

/** The server says the session is over: the interface forgets it at once, wherever the person was. */
export function useSessionEndedListener(): void {
  const client = useQueryClient()
  useEffect(() => {
    const onEnded = () => {
      setLoginReason('ended')
      endSession(client)
    }
    window.addEventListener(SESSION_ENDED_EVENT, onEnded)
    return () => window.removeEventListener(SESSION_ENDED_EVENT, onEnded)
  }, [client])
}
