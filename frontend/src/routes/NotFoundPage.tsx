import { Link } from 'react-router'
import { NOT_FOUND } from '@/lib/texts.fr'
import { useDocumentTitle } from '@/lib/useDocumentTitle'

export function NotFoundPage() {
  useDocumentTitle(NOT_FOUND.pageTitle)
  return (
    <main className="mx-auto flex min-h-svh max-w-lg flex-col justify-center gap-4 p-6">
      <h1 className="text-2xl font-semibold">{NOT_FOUND.title}</h1>
      <p className="text-muted-foreground">{NOT_FOUND.text}</p>
      <p>
        <Link to="/">{NOT_FOUND.back}</Link>
      </p>
    </main>
  )
}
