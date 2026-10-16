import { Spinner } from '@/components/ui/spinner'

/** A whole-page waiting message, announced politely to screen readers. */
export function FullPageStatus({ message }: { message: string }) {
  return (
    <main className="flex min-h-svh items-center justify-center p-6">
      <p role="status" className="flex items-center gap-3 text-lg text-muted-foreground">
        <Spinner aria-hidden="true" />
        {message}
      </p>
    </main>
  )
}
