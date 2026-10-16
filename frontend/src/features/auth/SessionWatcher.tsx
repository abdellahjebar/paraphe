import { useRef } from 'react'
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { SESSION_WARNING } from '@/lib/texts.fr'
import { formatCountdown } from '@/features/auth/sessionClock'
import { useLogout } from '@/features/auth/useLogout'
import { useSessionWatch } from '@/features/auth/useSessionWatch'

/**
 * Keeps the session alive while the person works, and warns them shortly before it ends (WCAG 2.2.1): a dialog
 * with the time left, a clear way to stay, and a way to leave. The focus lands on "stay connected".
 */
export function SessionWatcher({ timeoutSeconds }: { timeoutSeconds: number }) {
  const { warning, remainingMs, stayConnected } = useSessionWatch(timeoutSeconds)
  const logout = useLogout()
  const stayRef = useRef<HTMLButtonElement>(null)

  return (
    <AlertDialog
      open={warning}
      // Escape means "I am here": the safe answer.
      onOpenChange={(open) => {
        if (!open) stayConnected()
      }}
    >
      <AlertDialogContent initialFocus={stayRef}>
        <AlertDialogHeader>
          <AlertDialogTitle>{SESSION_WARNING.title}</AlertDialogTitle>
          <AlertDialogDescription>
            {SESSION_WARNING.description}{' '}
            {/* A timer is not announced second by second: the description is read once, when the dialog opens. */}
            <span role="timer" aria-live="off" className="font-semibold text-foreground">
              {formatCountdown(remainingMs)}
            </span>
            .
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <Button
            variant="outline"
            disabled={logout.isPending}
            focusableWhenDisabled
            onClick={() => logout.mutate()}
          >
            {SESSION_WARNING.signOut}
          </Button>
          <Button ref={stayRef} onClick={stayConnected}>
            {SESSION_WARNING.stay}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
