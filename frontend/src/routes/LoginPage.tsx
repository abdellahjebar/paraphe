import { LoginForm } from '@/features/auth/LoginForm'
import type { SessionUser } from '@/features/auth/session'
import { useDocumentTitle } from '@/lib/useDocumentTitle'
import { LOGIN } from '@/lib/texts.fr'

interface LoginPageProps {
  onSignedIn: (user: SessionUser) => void
}

export function LoginPage({ onSignedIn }: LoginPageProps) {
  useDocumentTitle(LOGIN.pageTitle)
  return (
    <main className="flex min-h-svh items-center justify-center p-6">
      <LoginForm onSignedIn={onSignedIn} />
    </main>
  )
}
