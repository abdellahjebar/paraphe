import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CSRF_COOKIE } from '@/lib/api/client'
import { createQueryClient } from '@/lib/query'
import { ChangePasswordForm } from '@/features/auth/ChangePasswordForm'
import { SESSION_KEY } from '@/features/auth/session'
import { accessibilityViolations } from '@/test/axe'

const CHANGED = {
  id: '5f0f5b7e-1c6e-4f0f-8f3e-0a6f2d7e9c11',
  email: 'khawla@exemple.test',
  displayName: 'Khawla',
  mustChangePassword: false,
  sessionIdleTimeoutSeconds: 3600,
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function server(answer: () => Promise<Response> | Response) {
  const requests: Request[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (request: Request) => {
      if (request.method === 'GET') {
        document.cookie = `${CSRF_COOKIE}=token; path=/`
        return json(401, { status: 401, code: 'AUTHENTICATION_REQUIRED' })
      }
      requests.push(request)
      return answer()
    }),
  )
  return { requests }
}

function setup() {
  const client = createQueryClient()
  const onChanged = vi.fn()
  const view = render(
    <QueryClientProvider client={client}>
      <ChangePasswordForm onChanged={onChanged} />
    </QueryClientProvider>,
  )
  return { client, onChanged, ...view, user: userEvent.setup() }
}

async function fill(user: ReturnType<typeof userEvent.setup>, current: string, next: string) {
  await user.type(screen.getByLabelText('Mot de passe provisoire'), current)
  await user.type(screen.getByLabelText('Nouveau mot de passe'), next)
}

describe('ChangePasswordForm', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('starts on the first field and tells password managers which is which', () => {
    server(() => json(200, CHANGED))
    setup()

    expect(screen.getByLabelText('Mot de passe provisoire')).toHaveFocus()
    expect(screen.getByLabelText('Mot de passe provisoire')).toHaveAttribute(
      'autocomplete',
      'current-password',
    )
    expect(screen.getByLabelText('Nouveau mot de passe')).toHaveAttribute('autocomplete', 'new-password')
  })

  it('states the rule once, in plain words, and ties it to the field', () => {
    server(() => json(200, CHANGED))
    setup()

    expect(screen.getByLabelText('Nouveau mot de passe')).toHaveAccessibleDescription(
      /Au moins 15 caractères/,
    )
  })

  it('has no automatic accessibility violation', async () => {
    server(() => json(200, CHANGED))
    const { container } = setup()
    expect(await accessibilityViolations(container)).toEqual([])
  })

  it('lists what is missing without sending anything', async () => {
    const { requests } = server(() => json(200, CHANGED))
    const { user } = setup()

    await user.click(screen.getByRole('button', { name: 'Enregistrer et continuer' }))

    const summary = await screen.findByRole('alert')
    expect(summary).toHaveFocus()
    expect(summary).toHaveTextContent('Certains champs sont à corriger')
    expect(summary).toHaveTextContent('Saisissez votre mot de passe provisoire.')
    expect(summary).toHaveTextContent('Saisissez votre nouveau mot de passe.')
    expect(requests).toHaveLength(0)
  })

  it('shows a too short password with the length the server asked for, at the field and in the summary', async () => {
    server(() => json(422, { status: 422, code: 'WEAK_PASSWORD', reason: 'TOO_SHORT', limit: 15 }))
    const { user } = setup()
    await fill(user, 'provisoire-12345', 'trop court')

    await user.click(screen.getByRole('button', { name: 'Enregistrer et continuer' }))

    const summary = await screen.findByRole('alert')
    expect(summary).toHaveTextContent('Le mot de passe n’a pas été modifié')
    expect(summary).toHaveTextContent('Le mot de passe doit comporter au moins 15 caractères.')
    expect(summary).toHaveFocus()
    expect(screen.getByLabelText('Nouveau mot de passe')).toBeInvalid()
    expect(screen.getByLabelText('Nouveau mot de passe')).toHaveAccessibleDescription(
      /Au moins 15 caractères\. Une phrase.*Erreur\s*:\s*Le mot de passe doit comporter au moins 15 caractères\./,
    )
    // nothing typed is lost
    expect(screen.getByLabelText('Nouveau mot de passe')).toHaveValue('trop court')
  })

  it('tells a common password apart from a short one', async () => {
    server(() => json(422, { status: 422, code: 'WEAK_PASSWORD', reason: 'COMMON' }))
    const { user } = setup()
    await fill(user, 'provisoire-12345', 'motdepassemotdepasse')

    await user.click(screen.getByRole('button', { name: 'Enregistrer et continuer' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ce mot de passe est trop courant.')
  })

  it('points at the temporary password when that is the wrong one', async () => {
    server(() => json(400, { status: 400, code: 'INVALID_CURRENT_PASSWORD' }))
    const { user } = setup()
    await fill(user, 'pas le bon', 'une nouvelle phrase de passe')

    await user.click(screen.getByRole('button', { name: 'Enregistrer et continuer' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Le mot de passe actuel est incorrect.')
    expect(screen.getByLabelText('Mot de passe provisoire')).toBeInvalid()
    expect(screen.getByLabelText('Nouveau mot de passe')).not.toBeInvalid()
  })

  it('shows any other failure for the form as a whole', async () => {
    server(() => json(500, { status: 500, code: 'INTERNAL_ERROR', correlationId: 'abc' }))
    const { user } = setup()
    await fill(user, 'provisoire-12345', 'une nouvelle phrase de passe')

    await user.click(screen.getByRole('button', { name: 'Enregistrer et continuer' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Un problème est survenu de notre côté.')
    expect(screen.getByLabelText('Nouveau mot de passe')).not.toBeInvalid()
  })

  it('makes the account the session, without the obligation, and hands it over', async () => {
    const { requests } = server(() => json(200, CHANGED))
    const { user, client, onChanged } = setup()
    await fill(user, 'provisoire-12345', 'une nouvelle phrase de passe')

    await user.click(screen.getByRole('button', { name: 'Enregistrer et continuer' }))

    await waitFor(() => expect(onChanged).toHaveBeenCalledWith(CHANGED))
    expect(client.getQueryData(SESSION_KEY)).toEqual(CHANGED)
    expect(await requests[0]?.json()).toEqual({
      currentPassword: 'provisoire-12345',
      newPassword: 'une nouvelle phrase de passe',
    })
  })

  it('shows and hides each password on its own', async () => {
    server(() => json(200, CHANGED))
    const { user } = setup()
    const [first] = screen.getAllByRole('button', { name: 'Afficher le mot de passe' })

    await user.click(first as HTMLElement)

    expect(screen.getByLabelText('Mot de passe provisoire')).toHaveAttribute('type', 'text')
    expect(screen.getByLabelText('Nouveau mot de passe')).toHaveAttribute('type', 'password')
  })
})
