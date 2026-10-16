import { Navigate, useNavigate } from 'react-router'
import { HOME_PATH, useSessionUser } from '@/features/auth/guards'
import { ChangePasswordForm } from '@/features/auth/ChangePasswordForm'
import { CHANGE_PASSWORD } from '@/lib/texts.fr'
import { useDocumentTitle } from '@/lib/useDocumentTitle'

export function ChangePasswordPage() {
  useDocumentTitle(CHANGE_PASSWORD.pageTitle)
  const user = useSessionUser()
  const navigate = useNavigate()

  // Someone who has nothing to change has no reason to be here.
  if (!user.mustChangePassword) return <Navigate to={HOME_PATH} replace />

  return (
    <main className="flex min-h-svh items-center justify-center p-6">
      <ChangePasswordForm onChanged={() => navigate(HOME_PATH, { replace: true })} />
    </main>
  )
}
