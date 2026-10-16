import { Navigate, Outlet, useLocation, useOutletContext } from 'react-router'
import { FullPageStatus } from '@/components/app/FullPageStatus'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { errorMessage } from '@/lib/messages.fr'
import { SESSION } from '@/lib/texts.fr'
import { useLoginReason, useSession, type LoginReason, type SessionUser } from '@/features/auth/session'

export const LOGIN_PATH = '/connexion'
export const PASSWORD_PATH = '/mot-de-passe'
export const HOME_PATH = '/projets'

export interface LoginState {
  /** Where the person was going. Only a path of this application is ever followed. */
  from?: string
  reason?: LoginReason
}

/** A path inside this application, never an address elsewhere. */
export function safeLocalPath(path: unknown): string | undefined {
  return typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') ? path : undefined
}

function SessionProblem({ error, retry }: { error: unknown; retry: () => void }) {
  return (
    <main className="mx-auto max-w-lg p-6">
      <Alert variant="destructive">
        <AlertTitle>{SESSION.errorTitle}</AlertTitle>
        <AlertDescription>{errorMessage(error)}</AlertDescription>
      </Alert>
      <Button className="mt-4" onClick={retry}>
        {SESSION.retry}
      </Button>
    </main>
  )
}

/** Everything inside needs a signed-in person. */
export function RequireSession() {
  const session = useSession()
  const location = useLocation()
  const reason = useLoginReason()

  switch (session.status) {
    case 'loading':
      return <FullPageStatus message={SESSION.checking} />
    case 'error':
      return <SessionProblem error={session.error} retry={session.retry} />
    case 'signedOut': {
      const state: LoginState = {
        from: `${location.pathname}${location.search}`,
        reason,
      }
      return <Navigate to={LOGIN_PATH} replace state={state} />
    }
    case 'signedIn':
      // A new account can do nothing else until it has chosen its own password.
      if (session.user.mustChangePassword && location.pathname !== PASSWORD_PATH) {
        return <Navigate to={PASSWORD_PATH} replace />
      }
      return <Outlet context={session.user} />
  }
}

/** The signed-in person, as given by RequireSession. */
export function useSessionUser(): SessionUser {
  return useOutletContext<SessionUser>()
}

/** The sign-in page is for people who are not signed in. */
export function RedirectIfSignedIn() {
  const session = useSession()
  const location = useLocation()

  switch (session.status) {
    case 'loading':
      return <FullPageStatus message={SESSION.checking} />
    case 'signedIn': {
      const from = safeLocalPath((location.state as LoginState | null)?.from)
      const target = session.user.mustChangePassword ? PASSWORD_PATH : (from ?? HOME_PATH)
      return <Navigate to={target} replace />
    }
    default:
      // signed out, or the server could not be asked: the form is still the right thing to show
      return <Outlet />
  }
}
