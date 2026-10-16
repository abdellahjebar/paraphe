import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ErrorSummary, type ErrorSummaryItem } from '@/components/app/ErrorSummary'
import { PasswordInput } from '@/components/app/PasswordInput'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader } from '@/components/ui/card'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { errorMessage } from '@/lib/messages.fr'
import { COMMON, LOGIN } from '@/lib/texts.fr'
import { useLogin } from '@/features/auth/useLogin'
import type { SessionUser } from '@/features/auth/session'

const EMAIL_ID = 'login-email'
const PASSWORD_ID = 'login-password'

interface LoginFormProps {
  onSignedIn: (user: SessionUser) => void
}

/**
 * The sign-in form. Checked when sent, not while typing; what was typed is never cleared; every problem is
 * listed in a summary that takes the focus and repeated at the field (decisions 37 to 39).
 */
export function LoginForm({ onSignedIn }: LoginFormProps) {
  const login = useLogin()
  const emailRef = useRef<HTMLInputElement>(null)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [missing, setMissing] = useState<{ email?: string; password?: string }>({})
  const [serverError, setServerError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    emailRef.current?.focus()
  }, [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    // One request at a time: a second click while the first is in flight changes nothing.
    if (login.isPending) return

    const problems = {
      email: email.trim() === '' ? LOGIN.emailRequired : undefined,
      password: password === '' ? LOGIN.passwordRequired : undefined,
    }
    setMissing(problems)
    setServerError(null)
    if (problems.email || problems.password) {
      setAttempt((count) => count + 1)
      return
    }

    try {
      const user = await login.mutateAsync({ email, password })
      onSignedIn(user)
    } catch (error) {
      setServerError(errorMessage(error))
      setAttempt((count) => count + 1)
    }
  }

  const summary: ErrorSummaryItem[] = []
  if (missing.email) summary.push({ id: 'email', message: missing.email, fieldId: EMAIL_ID })
  if (missing.password) summary.push({ id: 'password', message: missing.password, fieldId: PASSWORD_ID })
  const summaryTitle = serverError && summary.length === 0 ? LOGIN.failureTitle : COMMON.errorSummaryTitle
  if (serverError && summary.length === 0) summary.push({ id: 'server', message: serverError })

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <h1 className="text-2xl leading-snug font-semibold">{LOGIN.title}</h1>
        <CardDescription className="text-base">{LOGIN.description}</CardDescription>
      </CardHeader>
      <form noValidate onSubmit={submit}>
        <CardContent className="space-y-5">
          <ErrorSummary title={summaryTitle} items={summary} attempt={attempt} />
          <Field data-invalid={missing.email ? 'true' : undefined}>
            <FieldLabel htmlFor={EMAIL_ID}>{LOGIN.emailLabel}</FieldLabel>
            <Input
              ref={emailRef}
              id={EMAIL_ID}
              name="email"
              type="email"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              aria-invalid={missing.email ? true : undefined}
              aria-describedby={missing.email ? `${EMAIL_ID}-error` : undefined}
            />
            {missing.email && <FieldError id={`${EMAIL_ID}-error`}>{missing.email}</FieldError>}
          </Field>
          <Field data-invalid={missing.password ? 'true' : undefined}>
            <FieldLabel htmlFor={PASSWORD_ID}>{LOGIN.passwordLabel}</FieldLabel>
            <PasswordInput
              id={PASSWORD_ID}
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={missing.password ? true : undefined}
              aria-describedby={missing.password ? `${PASSWORD_ID}-error` : undefined}
            />
            {missing.password && <FieldError id={`${PASSWORD_ID}-error`}>{missing.password}</FieldError>}
          </Field>
        </CardContent>
        <CardFooter className="mt-6">
          <Button type="submit" className="w-full" disabled={login.isPending} focusableWhenDisabled>
            {login.isPending ? (
              <>
                <Spinner aria-hidden="true" /> {LOGIN.submitting}
              </>
            ) : (
              LOGIN.submit
            )}
          </Button>
        </CardFooter>
      </form>
    </Card>
  )
}
