import { ChangePasswordForm } from '@/features/auth/ChangePasswordForm'
import type { SessionUser } from '@/features/auth/session'
import { CHANGE_PASSWORD } from '@/lib/texts.fr'
import { useDocumentTitle } from '@/lib/useDocumentTitle'

interface ChangePasswordPageProps {
  onChanged: (user: SessionUser) => void
}

export function ChangePasswordPage({ onChanged }: ChangePasswordPageProps) {
  useDocumentTitle(CHANGE_PASSWORD.pageTitle)
  return (
    <main className="flex min-h-svh items-center justify-center p-6">
      <ChangePasswordForm onChanged={onChanged} />
    </main>
  )
}
