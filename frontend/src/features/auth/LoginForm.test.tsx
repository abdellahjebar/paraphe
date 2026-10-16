import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CSRF_COOKIE } from '@/lib/api/client'
import { createQueryClient } from '@/lib/query'
import { LoginForm } from '@/features/auth/LoginForm'
import { SESSION_KEY } from '@/features/auth/session'
import { accessibilityViolations } from '@/test/axe'

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

/** A fake server: the sign-in answer is chosen by the test; everything else is the cookie hand-out. */
function server(signIn: () => Promise<Response> | Response) {
  const logins: Request[] = []
  const handler = vi.fn(async (request: Request) => {
    if (request.method === 'GET') {
      document.cookie = `${CSRF_COOKIE}=token; path=/`
      return json(401, { status: 401, code: 'AUTHENTICATION_REQUIRED' })
    }
    logins.push(request)
    return signIn()
  })
  vi.stubGlobal('fetch', handler)
  return { logins }
}

function setup(onSignedIn = vi.fn()) {
  const client = createQueryClient()
  const view = render(
    <QueryClientProvider client={client}>
      <LoginForm onSignedIn={onSignedIn} />
    </QueryClientProvider>,
  )
  return { client, onSignedIn, ...view, user: userEvent.setup() }
}

describe('LoginForm', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('names its fields, tells browsers what they are and starts on the first one', () => {
    server(() => json(200, USER))
    setup()

    const email = screen.getByLabelText('Adresse e-mail')
    const password = screen.getByLabelText('Mot de passe')
    expect(email).toHaveAttribute('type', 'email')
    expect(email).toHaveAttribute('autocomplete', 'username')
    expect(password).toHaveAttribute('autocomplete', 'current-password')
    expect(password).toHaveAttribute('type', 'password')
    expect(email).toHaveFocus()
  })

  it('has no automatic accessibility violation', async () => {
    server(() => json(200, USER))
    const { container } = setup()
    expect(await accessibilityViolations(container)).toEqual([])
  })

  it('lists what is missing, repeats it at the field, moves the focus and sends nothing', async () => {
    const { logins } = server(() => json(200, USER))
    const { user } = setup()

    await user.click(screen.getByRole('button', { name: 'Se connecter' }))

    const summary = await screen.findByRole('alert')
    expect(summary).toHaveFocus()
    expect(summary).toHaveTextContent('Certains champs sont à corriger')
    expect(summary).toHaveTextContent('Saisissez votre adresse e-mail.')
    expect(summary).toHaveTextContent('Saisissez votre mot de passe.')
    // At the field, with the hidden "Erreur :" prefix for screen readers.
    expect(screen.getByLabelText('Adresse e-mail')).toHaveAccessibleDescription(
      /Erreur\s*:\s*Saisissez votre adresse e-mail\./,
    )
    expect(screen.getByLabelText('Adresse e-mail')).toBeInvalid()
    expect(logins).toHaveLength(0)
  })

  it('jumps to the field when its message in the summary is chosen', async () => {
    server(() => json(200, USER))
    const { user } = setup()
    await user.click(screen.getByRole('button', { name: 'Se connecter' }))

    await user.click(await screen.findByRole('link', { name: 'Saisissez votre mot de passe.' }))

    expect(screen.getByLabelText('Mot de passe')).toHaveFocus()
  })

  it('says the credentials are wrong without clearing anything that was typed', async () => {
    server(() => json(401, { status: 401, code: 'INVALID_CREDENTIALS' }))
    const { user, onSignedIn } = setup()
    await user.type(screen.getByLabelText('Adresse e-mail'), 'khawla@exemple.test')
    await user.type(screen.getByLabelText('Mot de passe'), 'pas le bon mot de passe')

    await user.click(screen.getByRole('button', { name: 'Se connecter' }))

    const summary = await screen.findByRole('alert')
    expect(summary).toHaveTextContent('Connexion impossible')
    expect(summary).toHaveTextContent('L’adresse e-mail ou le mot de passe est incorrect.')
    expect(summary).toHaveFocus()
    expect(screen.getByLabelText('Adresse e-mail')).toHaveValue('khawla@exemple.test')
    expect(screen.getByLabelText('Mot de passe')).toHaveValue('pas le bon mot de passe')
    expect(onSignedIn).not.toHaveBeenCalled()
  })

  it('explains a lock-out in its own words, never with the server text', async () => {
    server(() =>
      json(429, { status: 429, code: 'TOO_MANY_ATTEMPTS', detail: 'Account locked for 15 minutes' }),
    )
    const { user } = setup()
    await user.type(screen.getByLabelText('Adresse e-mail'), 'khawla@exemple.test')
    await user.type(screen.getByLabelText('Mot de passe'), 'x')

    await user.click(screen.getByRole('button', { name: 'Se connecter' }))

    const summary = await screen.findByRole('alert')
    expect(summary).toHaveTextContent('Trop de tentatives de connexion')
    expect(summary).not.toHaveTextContent('Account locked')
  })

  it('signs in: hands over the user and makes them the session', async () => {
    server(() => json(200, USER))
    const { user, onSignedIn, client } = setup()
    await user.type(screen.getByLabelText('Adresse e-mail'), '  khawla@exemple.test ')
    await user.type(screen.getByLabelText('Mot de passe'), 'une phrase de passe assez longue')

    await user.click(screen.getByRole('button', { name: 'Se connecter' }))

    await waitFor(() => expect(onSignedIn).toHaveBeenCalledWith(USER))
    expect(client.getQueryData(SESSION_KEY)).toEqual(USER)
  })

  it('sends the address without its surrounding spaces and the password exactly as typed', async () => {
    const { logins } = server(() => json(200, USER))
    const { user, onSignedIn } = setup()
    await user.type(screen.getByLabelText('Adresse e-mail'), '  khawla@exemple.test ')
    await user.type(screen.getByLabelText('Mot de passe'), ' avec des espaces ')

    await user.click(screen.getByRole('button', { name: 'Se connecter' }))

    await waitFor(() => expect(onSignedIn).toHaveBeenCalled())
    expect(await logins[0]?.json()).toEqual({ email: 'khawla@exemple.test', password: ' avec des espaces ' })
  })

  it('sends one request only when the button is pressed twice', async () => {
    let finish: (response: Response) => void = () => undefined
    const { logins } = server(() => new Promise<Response>((resolve) => (finish = resolve)))
    const { user } = setup()
    await user.type(screen.getByLabelText('Adresse e-mail'), 'khawla@exemple.test')
    await user.type(screen.getByLabelText('Mot de passe'), 'une phrase de passe assez longue')

    const button = screen.getByRole('button', { name: 'Se connecter' })
    await user.click(button)
    await user.click(screen.getByRole('button', { name: /Connexion en cours/ }))

    expect(logins).toHaveLength(1)
    finish(json(200, USER))
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /Connexion en cours/ })).not.toBeInTheDocument(),
    )
  })

  it('shows and hides the password on request', async () => {
    server(() => json(200, USER))
    const { user } = setup()
    const password = screen.getByLabelText('Mot de passe')
    const toggle = screen.getByRole('button', { name: 'Afficher le mot de passe' })
    expect(toggle).toHaveAttribute('aria-pressed', 'false')

    await user.click(toggle)
    expect(password).toHaveAttribute('type', 'text')
    expect(toggle).toHaveAttribute('aria-pressed', 'true')

    await user.click(toggle)
    expect(password).toHaveAttribute('type', 'password')
  })

  it('lets a password be pasted', async () => {
    server(() => json(200, USER))
    const { user } = setup()
    await user.click(screen.getByLabelText('Mot de passe'))

    await user.paste('collé depuis un gestionnaire')

    expect(screen.getByLabelText('Mot de passe')).toHaveValue('collé depuis un gestionnaire')
  })
})
