import { useLocation, useNavigate } from 'react-router'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { HOME_PATH, PASSWORD_PATH, safeLocalPath, type LoginState } from '@/features/auth/guards'
import { LoginForm } from '@/features/auth/LoginForm'
import { LOGIN, SESSION } from '@/lib/texts.fr'
import { useDocumentTitle } from '@/lib/useDocumentTitle'

const NOTICES = { ended: SESSION.ended, signedOut: SESSION.signedOut } as const

export function LoginPage() {
  useDocumentTitle(LOGIN.pageTitle)
  const navigate = useNavigate()
  const state = useLocation().state as LoginState | null
  const notice = state?.reason ? NOTICES[state.reason] : undefined

  return (
    <main className="flex min-h-svh items-center justify-center p-6">
      <div className="w-full max-w-sm space-y-4">
        {notice && (
          <Alert role="status">
            <AlertDescription>{notice}</AlertDescription>
          </Alert>
        )}
        <LoginForm
          onSignedIn={(user) =>
            navigate(user.mustChangePassword ? PASSWORD_PATH : (safeLocalPath(state?.from) ?? HOME_PATH), {
              replace: true,
            })
          }
        />
      </div>
    </main>
  )
}
