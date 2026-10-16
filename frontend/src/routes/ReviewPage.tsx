import { COMING } from '@/lib/texts.fr'
import { useDocumentTitle } from '@/lib/useDocumentTitle'

/**
 * A placeholder for the reviewer's page, which arrives in step 4.4. It is public: no session is asked about,
 * and the token in the address is not read, shown or sent anywhere.
 */
export function ReviewPage() {
  useDocumentTitle(COMING.reviewTitle)
  return (
    <main className="mx-auto max-w-lg p-6">
      <h1 className="text-2xl font-semibold">{COMING.reviewTitle}</h1>
      <p className="text-muted-foreground">{COMING.text}</p>
    </main>
  )
}
