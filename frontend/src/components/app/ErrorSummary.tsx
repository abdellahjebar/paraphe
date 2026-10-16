import { useEffect, useRef } from 'react'
import { cn } from '@/lib/utils'

export interface ErrorSummaryItem {
  /** Stable key for the list. */
  id: string
  message: string
  /** The field the message is about. The link focuses it. Without it the message is shown as plain text. */
  fieldId?: string
}

interface ErrorSummaryProps {
  title: string
  items: ErrorSummaryItem[]
  /** Changes on every failed attempt, so the summary takes the focus again even when the errors are the same. */
  attempt: number
  className?: string
}

/**
 * Lists everything that went wrong at the top of a form and takes the keyboard focus, so a screen reader reads
 * it and the person can jump to each field. The same words appear at the field itself.
 */
export function ErrorSummary({ title, items, attempt, className }: ErrorSummaryProps) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (attempt > 0 && items.length > 0) ref.current?.focus()
    // Only a new attempt moves the focus, not a re-render with the same errors.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attempt])

  if (items.length === 0) return null

  function focusField(fieldId: string) {
    const field = document.getElementById(fieldId)
    // Focusing a field already brings it into view.
    field?.focus()
  }

  return (
    <div
      ref={ref}
      role="alert"
      tabIndex={-1}
      data-slot="error-summary"
      className={cn('rounded-lg border-2 border-destructive p-4 text-destructive outline-none', className)}
    >
      <h2 className="text-base font-semibold">{title}</h2>
      <ul className="mt-2 flex list-disc flex-col gap-1 pl-5">
        {items.map((item) => (
          <li key={item.id}>
            {item.fieldId ? (
              <a
                href={`#${item.fieldId}`}
                className="font-semibold text-destructive underline"
                onClick={(event) => {
                  event.preventDefault()
                  focusField(item.fieldId as string)
                }}
              >
                {item.message}
              </a>
            ) : (
              item.message
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
