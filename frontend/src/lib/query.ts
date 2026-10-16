import { QueryClient } from '@tanstack/react-query'
import { isApiError } from '@/lib/api/errors'

/**
 * One cache for the whole interface.
 *
 * Nothing is retried when the server has answered: a refusal will not change by asking again, and a second
 * attempt at a write could do harm. A server that cannot be reached is tried twice more, because that is
 * often a passing hiccup.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) =>
          isApiError(error) && error.code === 'NETWORK_ERROR' && failureCount < 2,
        refetchOnWindowFocus: false,
      },
      mutations: { retry: false },
    },
  })
}
