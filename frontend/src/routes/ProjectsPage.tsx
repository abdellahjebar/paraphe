import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { useSessionUser } from '@/features/auth/guards'
import { useLogout } from '@/features/auth/useLogout'
import { COMING, SESSION } from '@/lib/texts.fr'
import { useDocumentTitle } from '@/lib/useDocumentTitle'

/** A placeholder: the list of sites arrives with the next step. */
export function ProjectsPage() {
  const user = useSessionUser()
  const logout = useLogout()
  useDocumentTitle(COMING.projectsTitle)

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="text-2xl font-semibold">{COMING.projectsTitle}</h1>
      <p className="text-muted-foreground">
        {user.displayName}, {COMING.text.toLowerCase()}
      </p>
      <Button
        variant="outline"
        disabled={logout.isPending}
        focusableWhenDisabled
        onClick={() => logout.mutate()}
      >
        {logout.isPending ? (
          <>
            <Spinner aria-hidden="true" /> {SESSION.signingOut}
          </>
        ) : (
          SESSION.signOut
        )}
      </Button>
    </main>
  )
}
