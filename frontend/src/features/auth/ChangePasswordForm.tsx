import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ErrorSummary, type ErrorSummaryItem } from '@/components/app/ErrorSummary'
import { PasswordInput } from '@/components/app/PasswordInput'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader } from '@/components/ui/card'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Spinner } from '@/components/ui/spinner'
import { isApiError } from '@/lib/api/errors'
import { errorMessage } from '@/lib/messages.fr'
import { CHANGE_PASSWORD, COMMON } from '@/lib/texts.fr'
import type { SessionUser } from '@/features/auth/session'
import { useChangePassword } from '@/features/auth/useChangePassword'

const CURRENT_ID = 'password-current'
const NEW_ID = 'password-new'

type FieldKey = 'current' | 'new'
type FieldProblems = Partial<Record<FieldKey, string>>

interface ChangePasswordFormProps {
  onChanged: (user: SessionUser) => void
}

/** Which field a refusal from the server is about, if any. The others are shown for the form as a whole. */
function fieldOfRefusal(error: unknown): FieldKey | null {
  if (!isApiError(error)) return null
  if (error.code === 'INVALID_CURRENT_PASSWORD') return 'current'
  if (error.code === 'WEAK_PASSWORD') return 'new'
  return null
}

/**
 * The first thing a new account must do. There is no confirmation field: the person can show what they typed,
 * which is clearer and one field less (decision 42). Rules are not pre-checked here, the server is the one
 * authority and says why in its own words (length, common password, ...).
 */
export function ChangePasswordForm({ onChanged }: ChangePasswordFormProps) {
  const change = useChangePassword()
  const currentRef = useRef<HTMLInputElement>(null)
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [problems, setProblems] = useState<FieldProblems>({})
  const [general, setGeneral] = useState<string | null>(null)
  /** True when the server refused the request, false when the form itself found something missing. */
  const [refused, setRefused] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    currentRef.current?.focus()
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (change.isPending) return

    const missing: FieldProblems = {
      current: current === '' ? CHANGE_PASSWORD.currentRequired : undefined,
      new: next === '' ? CHANGE_PASSWORD.newRequired : undefined,
    }
    setGeneral(null)
    if (missing.current || missing.new) {
      setRefused(false)
      setProblems(missing)
      setAttempt((count) => count + 1)
      return
    }
    setProblems({})

    try {
      onChanged(await change.mutateAsync({ currentPassword: current, newPassword: next }))
    } catch (error) {
      setRefused(true)
      const field = fieldOfRefusal(error)
      if (field) setProblems({ [field]: errorMessage(error) })
      else setGeneral(errorMessage(error))
      setAttempt((count) => count + 1)
    }
  }

  const summary: ErrorSummaryItem[] = []
  if (problems.current) summary.push({ id: 'current', message: problems.current, fieldId: CURRENT_ID })
  if (problems.new) summary.push({ id: 'new', message: problems.new, fieldId: NEW_ID })
  if (general) summary.push({ id: 'general', message: general })
  const summaryTitle = refused ? CHANGE_PASSWORD.failureTitle : COMMON.errorSummaryTitle

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <h1 className="text-2xl leading-snug font-semibold">{CHANGE_PASSWORD.title}</h1>
        <CardDescription className="text-base">{CHANGE_PASSWORD.description}</CardDescription>
      </CardHeader>
      <form noValidate onSubmit={submit}>
        <CardContent className="space-y-5">
          <ErrorSummary title={summaryTitle} items={summary} attempt={attempt} />
          <Field data-invalid={problems.current ? 'true' : undefined}>
            <FieldLabel htmlFor={CURRENT_ID}>{CHANGE_PASSWORD.currentLabel}</FieldLabel>
            <PasswordInput
              ref={currentRef}
              id={CURRENT_ID}
              name="current-password"
              autoComplete="current-password"
              value={current}
              onChange={(event) => setCurrent(event.target.value)}
              aria-invalid={problems.current ? true : undefined}
              aria-describedby={problems.current ? `${CURRENT_ID}-error` : undefined}
            />
            {problems.current && <FieldError id={`${CURRENT_ID}-error`}>{problems.current}</FieldError>}
          </Field>
          <Field data-invalid={problems.new ? 'true' : undefined}>
            <FieldLabel htmlFor={NEW_ID}>{CHANGE_PASSWORD.newLabel}</FieldLabel>
            <PasswordInput
              id={NEW_ID}
              name="new-password"
              autoComplete="new-password"
              value={next}
              onChange={(event) => setNext(event.target.value)}
              aria-invalid={problems.new ? true : undefined}
              aria-describedby={problems.new ? `${NEW_ID}-hint ${NEW_ID}-error` : `${NEW_ID}-hint`}
            />
            <FieldDescription id={`${NEW_ID}-hint`}>{CHANGE_PASSWORD.newHint}</FieldDescription>
            {problems.new && <FieldError id={`${NEW_ID}-error`}>{problems.new}</FieldError>}
          </Field>
        </CardContent>
        <CardFooter className="mt-6">
          <Button type="submit" className="w-full" disabled={change.isPending} focusableWhenDisabled>
            {change.isPending ? (
              <>
                <Spinner aria-hidden="true" /> {CHANGE_PASSWORD.submitting}
              </>
            ) : (
              CHANGE_PASSWORD.submit
            )}
          </Button>
        </CardFooter>
      </form>
    </Card>
  )
}
