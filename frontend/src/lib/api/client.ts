import createClient, { type Middleware } from 'openapi-fetch'
import { ApiError, isProblem } from '@/lib/api/errors'
import type { paths } from '@/lib/api/schema'

/** The cookie the server hands out, and the header it expects back on every write (double-submit CSRF). */
export const CSRF_COOKIE = 'XSRF-TOKEN'
export const CSRF_HEADER = 'X-XSRF-TOKEN'

/** Fired when the server says the session is over, so the interface can send the person to sign in again. */
export const SESSION_ENDED_EVENT = 'paraphe:session-ended'

/** Fired on every answer from the server, whatever it says: a session is kept alive by any request. */
export const API_CONTACT_EVENT = 'paraphe:api-contact'

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

function readCookie(name: string): string | undefined {
  for (const part of document.cookie.split('; ')) {
    const separator = part.indexOf('=')
    if (separator > 0 && part.slice(0, separator) === name) {
      return decodeURIComponent(part.slice(separator + 1))
    }
  }
  return undefined
}

type Fetch = (input: Request) => Promise<Response>

export interface ApiOptions {
  /** Replaces the browser's fetch. Used by tests. */
  fetch?: Fetch
}

/**
 * The typed client for the contractor API (`/api/**`) and the reviewer API (`/r/**`).
 *
 * - Every write carries the CSRF token. The cookie only exists once the server has answered a first call, so
 *   a write made before that makes one harmless read first.
 * - Every refusal becomes an {@link ApiError} carrying the server's stable `code`.
 * - A call that cannot reach the server becomes an ApiError with the code `NETWORK_ERROR`.
 */
export function createApi(options: ApiOptions = {}) {
  const send: Fetch = options.fetch ?? ((request) => fetch(request))
  // Absolute, so a Request can be built outside a browser page too (tests). In the browser it is the page's own origin.
  const client = createClient<paths>({
    baseUrl: window.location.origin,
    credentials: 'same-origin',
    fetch: send,
  })

  async function ensureCsrfCookie(): Promise<void> {
    if (readCookie(CSRF_COOKIE) !== undefined) return
    try {
      // Any /api call makes the server set the cookie, even one that is refused with 401.
      await send(new Request(new URL('/api/auth/me', window.location.origin), { credentials: 'same-origin' }))
    } catch {
      /* the write that follows reports the network failure */
    }
  }

  const csrf: Middleware = {
    async onRequest({ request }) {
      const { pathname } = new URL(request.url)
      // The reviewer API has no cookie, no session and no CSRF: its token is the credential.
      if (SAFE_METHODS.has(request.method) || !pathname.startsWith('/api/')) return request
      await ensureCsrfCookie()
      const token = readCookie(CSRF_COOKIE)
      if (token !== undefined) request.headers.set(CSRF_HEADER, token)
      return request
    },
  }

  const errors: Middleware = {
    async onResponse({ response, schemaPath }) {
      window.dispatchEvent(new CustomEvent<number>(API_CONTACT_EVENT, { detail: Date.now() }))
      if (response.ok) return response

      let body: unknown
      try {
        body = await response.clone().json()
      } catch {
        body = undefined
      }
      const error = isProblem(body)
        ? new ApiError(response.status, body.code, body)
        : new ApiError(response.status, 'UNEXPECTED_RESPONSE')

      // "Am I signed in?" is allowed to be answered with 401: that is not a session that just ended.
      if (error.code === 'AUTHENTICATION_REQUIRED' && schemaPath !== '/api/auth/me') {
        window.dispatchEvent(new Event(SESSION_ENDED_EVENT))
      }
      throw error
    },
    onError() {
      return new ApiError(0, 'NETWORK_ERROR')
    },
  }

  client.use(csrf, errors)
  return client
}

export const api = createApi()
