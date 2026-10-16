import { useEffect } from 'react'
import { COMMON } from '@/lib/texts.fr'

/** Every page has its own title (WCAG 2.4.2): what it is, then the name of the product. */
export function useDocumentTitle(title: string): void {
  useEffect(() => {
    document.title = `${title} – ${COMMON.productName}`
  }, [title])
}
