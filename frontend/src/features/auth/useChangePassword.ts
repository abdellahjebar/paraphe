import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { SESSION_KEY, type SessionUser } from '@/features/auth/session'

export interface PasswordChange {
  currentPassword: string
  newPassword: string
}

/**
 * Changes the password of the signed-in account. The server answers with the account as it is now, with the
 * obligation to change lifted, and that becomes the session at once.
 */
export function useChangePassword() {
  const client = useQueryClient()
  return useMutation<SessionUser, Error, PasswordChange>({
    mutationFn: async (body) => {
      const { data } = await api.POST('/api/auth/password', { body })
      if (!data) throw new Error('Password change answered without a user')
      return data
    },
    onSuccess: (user) => client.setQueryData(SESSION_KEY, user),
  })
}
