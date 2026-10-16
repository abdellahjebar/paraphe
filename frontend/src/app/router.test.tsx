import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { routes } from '@/app/router'
import { CSRF_COOKIE, SESSION_ENDED_EVENT } from '@/lib/api/client'
import { createQueryClient } from '@/lib/query'
import { setLoginReason } from '@/features/auth/session'

const USER = {
  id: '5f0f5b7e-1c6e-4f0f-8f3e-0a6f2d7e9c11',
  email: 'khawla@exemple.test',
  displayName: 'Khawla',
  mustChangePassword: false,
  sessionIdleTimeoutSeconds: 3600,
}
const NEW_ACCOUNT = { ...USER, mustChangePassword: true }

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
const SIGNED_OUT = () => json(401, { status: 401, code: 'AUTHENTICATION_REQUIRED' })

/** A fake server. `me` says who is signed in; the other calls are answered as asked. */
function server(options: { me: () => Response; login?: () => Response; password?: () => Response }) {
  const calls: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (request: Request) => {
      const { pathname } = new URL(request.url)
      calls.push(`${request.method} ${pathname}`)
      if (pathname === '/api/auth/me') {
        document.cookie = `${CSRF_COOKIE}=token; path=/`
        return options.me()
      }
      if (pathname === '/api/auth/login')
        return options.login?.() ?? json(500, { status: 500, code: 'INTERNAL_ERROR' })
      if (pathname === '/api/auth/password')
        return options.password?.() ?? json(500, { status: 500, code: 'INTERNAL_ERROR' })
      if (pathname === '/api/auth/logout') return new Response(null, { status: 204 })
      return json(404, { status: 404, code: 'NOT_FOUND' })
    }),
  )
  return { calls }
}

function open(path: string) {
  const client = createQueryClient()
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return { client, router, user: userEvent.setup() }
}

async function signIn(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText('Adresse e-mail'), 'khawla@exemple.test')
  await user.type(screen.getByLabelText('Mot de passe'), 'une phrase de passe assez longue')
  await user.click(screen.getByRole('button', { name: 'Se connecter' }))
}

describe('routes', () => {
  beforeEach(() => setLoginReason(undefined))
  afterEach(() => vi.unstubAllGlobals())

  it('sends a visitor who is not signed in to the sign-in page, then back to where they were going', async () => {
    server({ me: SIGNED_OUT, login: () => json(200, USER) })
    const { user, router } = open('/projets')

    expect(await screen.findByRole('heading', { level: 1, name: 'Connexion' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/connexion')

    await signIn(user)

    expect(await screen.findByRole('heading', { level: 1, name: 'Mes chantiers' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/projets')
  })

  it('never follows a "from" that points outside the application', async () => {
    server({ me: SIGNED_OUT, login: () => json(200, USER) })
    const { user, router } = open('/connexion')
    // eslint-disable-next-line no-restricted-syntax -- an address elsewhere, on purpose: it must be refused
    await router.navigate('/connexion', { state: { from: '//elsewhere.example' }, replace: true })

    await signIn(user)

    await waitFor(() => expect(router.state.location.pathname).toBe('/projets'))
  })

  it('keeps a new account on the password page until it has chosen its own password', async () => {
    server({
      me: SIGNED_OUT,
      login: () => json(200, NEW_ACCOUNT),
      password: () => json(200, USER),
    })
    const { user, router } = open('/connexion')

    await signIn(user)

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Choisissez votre mot de passe' }),
    ).toBeInTheDocument()
    // trying to go elsewhere changes nothing
    await act(() => router.navigate('/projets'))
    await waitFor(() => expect(router.state.location.pathname).toBe('/mot-de-passe'))

    await user.type(screen.getByLabelText('Mot de passe provisoire'), 'provisoire-12345')
    await user.type(screen.getByLabelText('Nouveau mot de passe'), 'une nouvelle phrase de passe')
    await user.click(screen.getByRole('button', { name: 'Enregistrer et continuer' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Mes chantiers' })).toBeInTheDocument()
  })

  it('does not show the password page to someone who has nothing to change', async () => {
    server({ me: () => json(200, USER) })
    const { router } = open('/mot-de-passe')

    await waitFor(() => expect(router.state.location.pathname).toBe('/projets'))
  })

  it('does not show the sign-in page to someone who is already signed in', async () => {
    server({ me: () => json(200, USER) })
    const { router } = open('/connexion')

    expect(await screen.findByRole('heading', { level: 1, name: 'Mes chantiers' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/projets')
  })

  it('says so, and goes back to the page, when the server ends the session', async () => {
    server({ me: () => json(200, USER), login: () => json(200, USER) })
    const { router, user } = open('/projets')
    expect(await screen.findByRole('heading', { level: 1, name: 'Mes chantiers' })).toBeInTheDocument()

    act(() => {
      window.dispatchEvent(new Event(SESSION_ENDED_EVENT))
    })

    expect(await screen.findByRole('heading', { level: 1, name: 'Connexion' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(
      'Votre session est terminée. Connectez-vous à nouveau.',
    )
    expect(router.state.location.state).toMatchObject({ from: '/projets', reason: 'ended' })

    await signIn(user)
    expect(await screen.findByRole('heading', { level: 1, name: 'Mes chantiers' })).toBeInTheDocument()
  })

  it('signs out: back to the sign-in page, saying so, with nothing of the account left', async () => {
    const { calls } = server({ me: () => json(200, USER) })
    const { client, user } = open('/projets')
    await screen.findByRole('heading', { level: 1, name: 'Mes chantiers' })
    client.setQueryData(['projects'], [{ name: 'Chantier confidentiel' }])

    await user.click(screen.getByRole('button', { name: 'Se déconnecter' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Connexion' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Vous avez été déconnecté.')
    expect(calls).toContain('POST /api/auth/logout')
    expect(client.getQueryData(['projects'])).toBeUndefined()
  })

  it('forgets the account even when the sign-out call cannot reach the server', async () => {
    server({ me: () => json(200, USER) })
    const { client, user } = open('/projets')
    await screen.findByRole('heading', { level: 1, name: 'Mes chantiers' })
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch')
      }),
    )

    await user.click(screen.getByRole('button', { name: 'Se déconnecter' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Connexion' })).toBeInTheDocument()
    expect(client.getQueryData(['session'])).toBeNull()
  })

  it('offers a retry when the server cannot be asked who is signed in, instead of showing the sign-in page', async () => {
    server({ me: () => json(500, { status: 500, code: 'INTERNAL_ERROR' }) })
    open('/projets')

    expect(await screen.findByText('Impossible de vérifier votre connexion')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Réessayer' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Adresse e-mail')).not.toBeInTheDocument()
  })

  it('shows the reviewer page without asking about any session', async () => {
    const { calls } = server({ me: SIGNED_OUT })
    open('/relecture/jeton-secret-de-test')

    expect(await screen.findByRole('heading', { level: 1, name: 'Relecture' })).toBeInTheDocument()
    expect(calls).toEqual([])
    expect(document.body).not.toHaveTextContent('jeton-secret-de-test')
  })

  it('answers an unknown address with a page that says so', async () => {
    server({ me: SIGNED_OUT })
    open('/n-importe-quoi')

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Cette page n’existe pas' }),
    ).toBeInTheDocument()
    expect(document.title).toBe('Page introuvable – Paraphe')
  })

  it('sends the root to the sites', async () => {
    server({ me: () => json(200, USER) })
    const { router } = open('/')

    await waitFor(() => expect(router.state.location.pathname).toBe('/projets'))
  })
})
