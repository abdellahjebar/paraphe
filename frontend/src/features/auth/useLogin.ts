import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { setLoginReason, SESSION_KEY, type SessionUser } from '@/features/auth/session'

export interface Credentials {
  email: string
  password: string
}

/**
 * Signs in. On success the person becomes the session of the whole interface at once; on failure the call
 * rejects with an ApiError and nothing is remembered.
 */
export function useLogin() {
  const client = useQueryClient()
  return useMutation<SessionUser, Error, Credentials>({
    mutationFn: async ({ email, password }) => {
      const { data } = await api.POST('/api/auth/login', { body: { email: email.trim(), password } })
      if (!data) throw new Error('Sign-in answered without a user')
      return data
    },
    onSuccess: (user) => {
      // Whatever an earlier person left in memory is dropped before this one is shown anything.
      client.removeQueries({ predicate: (query) => query.queryKey[0] !== SESSION_KEY[0] })
      client.setQueryData(SESSION_KEY, user)
      setLoginReason(undefined)
    },
  })
}
