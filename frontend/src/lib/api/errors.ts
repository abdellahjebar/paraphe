import type { components } from '@/lib/api/schema'

/** The body of every error the API sends. */
export type Problem = components['schemas']['Problem']

/** What the server can answer. A closed set: the client must have a message for each one. */
export type ErrorCode = Problem['code']

/** Failures that never reach the server, or that it did not describe. */
export type ClientErrorCode = ErrorCode | 'NETWORK_ERROR' | 'UNEXPECTED_RESPONSE'

type FieldErrorList = NonNullable<Problem['errors']>
export type FieldError = FieldErrorList[number]
export type FieldErrorCode = FieldError['code']
export type WeakPasswordReason = NonNullable<Problem['reason']>

/**
 * A request the server refused, or one that could not be made.
 *
 * Read {@link code}, never the text: {@link Error.message} is for developers and stays in English.
 * What a person sees comes from `messages.fr.ts`.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: ClientErrorCode
  readonly fieldErrors: FieldErrorList
  readonly reason: WeakPasswordReason | undefined
  readonly limit: number | undefined
  /** Quote this to an administrator: it finds the failure in the server log. */
  readonly correlationId: string | undefined

  constructor(status: number, code: ClientErrorCode, problem?: Problem) {
    super(`${status} ${code}`)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = problem?.errors ?? []
    this.reason = problem?.reason
    this.limit = problem?.limit
    this.correlationId = problem?.correlationId
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError
}

/** Does this object look like the server's error body? Anything else is treated as an unexpected answer. */
export function isProblem(value: unknown): value is Problem {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.status === 'number' && typeof candidate.code === 'string'
}
