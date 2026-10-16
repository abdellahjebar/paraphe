import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { endSession, setLoginReason } from '@/features/auth/session'
import { broadcastSession } from '@/features/auth/sessionChannel'

/**
 * Signs out. Whatever the server answers, the interface forgets the account: someone who asked to leave must
 * never be left looking at their data because a network call failed. The route guards then send the person
 * to the sign-in page, which says they signed out.
 */
export function useLogout() {
  const client = useQueryClient()
  return useMutation<void, Error, void>({
    mutationFn: async () => {
      await api.POST('/api/auth/logout')
    },
    onSettled: () => {
      setLoginReason('signedOut')
      endSession(client)
      broadcastSession({ type: 'ended', reason: 'signedOut' })
    },
  })
}
