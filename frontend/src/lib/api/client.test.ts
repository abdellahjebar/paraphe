import { createApi, CSRF_COOKIE, CSRF_HEADER, SESSION_ENDED_EVENT } from '@/lib/api/client'
import { ApiError, isApiError } from '@/lib/api/errors'
import { errorMessage } from '@/lib/messages.fr'

type Fetch = (request: Request) => Promise<Response>

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function clearCookies() {
  for (const part of document.cookie.split('; ')) {
    const name = part.split('=')[0]
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  }
}

describe('API client', () => {
  beforeEach(clearCookies)

  it('sends the CSRF token on a write when the cookie is already there', async () => {
    document.cookie = `${CSRF_COOKIE}=abc123; path=/`
    const fetch = vi.fn<Fetch>(async () => new Response(null, { status: 204 }))
    const api = createApi({ fetch })

    await api.POST('/api/auth/logout')

    const request = fetch.mock.calls[0]?.[0] as unknown as Request
    expect(request.headers.get(CSRF_HEADER)).toBe('abc123')
    expect(request.credentials).toBe('same-origin')
  })

  it('makes one harmless read first to obtain the cookie when it is missing', async () => {
    const calls: string[] = []
    const fetch = vi.fn<Fetch>(async (request) => {
      calls.push(`${request.method} ${new URL(request.url).pathname}`)
      if (request.method === 'GET') {
        document.cookie = `${CSRF_COOKIE}=fresh; path=/`
        return json(401, { status: 401, code: 'AUTHENTICATION_REQUIRED' })
      }
      expect(request.headers.get(CSRF_HEADER)).toBe('fresh')
      return new Response(null, { status: 204 })
    })
    const api = createApi({ fetch })

    await api.POST('/api/auth/logout')

    expect(calls).toEqual(['GET /api/auth/me', 'POST /api/auth/logout'])
  })

  it('does not send the CSRF token on a read or on the reviewer API', async () => {
    document.cookie = `${CSRF_COOKIE}=abc123; path=/`
    const fetch = vi.fn<Fetch>(async () => json(200, []))
    const api = createApi({ fetch })

    await api.GET('/api/projects')
    const read = fetch.mock.calls[0]?.[0] as unknown as Request
    expect(read.headers.has(CSRF_HEADER)).toBe(false)

    fetch.mockImplementationOnce(async () => new Response(null, { status: 204 }))
    await api.POST('/r/{token}/seen', { params: { path: { token: 'x'.repeat(43) } } })
    const reviewer = fetch.mock.calls[1]?.[0] as unknown as Request
    expect(reviewer.headers.has(CSRF_HEADER)).toBe(false)
  })

  it('turns a refusal into an ApiError carrying the stable code and the field errors', async () => {
    document.cookie = `${CSRF_COOKIE}=abc123; path=/`
    const fetch = vi.fn<Fetch>(async () =>
      json(400, { status: 400, code: 'VALIDATION_FAILED', errors: [{ field: 'name', code: 'REQUIRED' }] }),
    )
    const api = createApi({ fetch })

    const failure = await api.POST('/api/projects', { body: { name: ' ' } }).catch((e: unknown) => e)

    expect(isApiError(failure)).toBe(true)
    const error = failure as ApiError
    expect(error.status).toBe(400)
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.fieldErrors).toEqual([{ field: 'name', code: 'REQUIRED' }])
  })

  it('keeps the reason and the limit of a refused password', async () => {
    document.cookie = `${CSRF_COOKIE}=abc123; path=/`
    const fetch = vi.fn<Fetch>(async () =>
      json(422, { status: 422, code: 'WEAK_PASSWORD', reason: 'TOO_SHORT', limit: 15 }),
    )
    const api = createApi({ fetch })

    const failure = (await api
      .POST('/api/auth/password', { body: { currentPassword: 'a', newPassword: 'b' } })
      .catch((e: unknown) => e)) as ApiError

    expect(errorMessage(failure)).toBe('Le mot de passe doit comporter au moins 15 caractères.')
  })

  it('treats an answer it cannot understand as unexpected, never as a success', async () => {
    const fetch = vi.fn<Fetch>(async () => new Response('<html>Bad gateway</html>', { status: 502 }))
    const api = createApi({ fetch })

    const failure = (await api.GET('/api/projects').catch((e: unknown) => e)) as ApiError

    expect(failure.code).toBe('UNEXPECTED_RESPONSE')
    expect(failure.status).toBe(502)
    expect(errorMessage(failure)).not.toContain('html')
  })

  it('reports a server that cannot be reached', async () => {
    const fetch = vi.fn<Fetch>(async () => {
      throw new TypeError('Failed to fetch')
    })
    const api = createApi({ fetch })

    const failure = (await api.GET('/api/projects').catch((e: unknown) => e)) as ApiError

    expect(failure.code).toBe('NETWORK_ERROR')
    expect(errorMessage(failure)).toMatch(/injoignable/)
  })

  it('announces the end of a session, except when merely asking who is signed in', async () => {
    const ended = vi.fn()
    window.addEventListener(SESSION_ENDED_EVENT, ended)
    const fetch = vi.fn<Fetch>(async () => json(401, { status: 401, code: 'AUTHENTICATION_REQUIRED' }))
    const api = createApi({ fetch })

    await api.GET('/api/auth/me').catch(() => undefined)
    expect(ended).not.toHaveBeenCalled()

    await api.GET('/api/projects').catch(() => undefined)
    expect(ended).toHaveBeenCalledTimes(1)
    window.removeEventListener(SESSION_ENDED_EVENT, ended)
  })

  it('never shows the server text, only its own French message', () => {
    const error = new ApiError(409, 'DUPLICATE_CODE', {
      status: 409,
      code: 'DUPLICATE_CODE',
      detail: "Work package 'Lot 01' already exists in this project",
    })
    expect(errorMessage(error)).toBe('Ce code existe déjà dans ce chantier. Choisissez-en un autre.')
    expect(errorMessage(new Error('boom'))).toBe(
      'Le serveur a répondu de façon inattendue. Réessayez dans un instant.',
    )
  })
})
