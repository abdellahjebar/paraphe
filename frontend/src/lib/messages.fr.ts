import {
  isApiError,
  type ClientErrorCode,
  type FieldErrorCode,
  type WeakPasswordReason,
} from '@/lib/api/errors'

/*
 * Every text an error can show, in French, formal register (decisions 5 and 39 in
 * docs/frontend/design-decisions.md): say what went wrong and how to put it right, with no blame and none of
 * "veuillez", "invalide" or "une erreur est survenue".
 *
 * A Record over the closed set of codes: adding a code to the API without a message here does not compile.
 */
export const ERROR_MESSAGES: Record<ClientErrorCode, string> = {
  AUTHENTICATION_REQUIRED: 'Votre session est terminée. Connectez-vous à nouveau.',
  FORBIDDEN: 'Cette action n’est pas autorisée. Rechargez la page, puis réessayez.',
  NOT_FOUND: 'Cet élément est introuvable, ou vous n’y avez pas accès.',
  TOO_MANY_REQUESTS: 'Trop de demandes en peu de temps. Patientez un instant, puis réessayez.',
  TOO_MANY_ATTEMPTS: 'Trop de tentatives de connexion. Patientez quelques minutes, puis réessayez.',
  INVALID_CREDENTIALS: 'L’adresse e-mail ou le mot de passe est incorrect.',
  INVALID_CURRENT_PASSWORD: 'Le mot de passe actuel est incorrect.',
  WEAK_PASSWORD: 'Ce mot de passe ne peut pas être accepté. Choisissez-en un autre.',
  VALIDATION_FAILED: 'Certains champs sont à corriger.',
  MALFORMED_REQUEST: 'La demande n’a pas pu être lue. Rechargez la page, puis réessayez.',
  INVALID_REQUEST: 'La demande n’a pas pu être traitée. Vérifiez les informations saisies.',
  METHOD_NOT_ALLOWED: 'Un problème technique empêche cette action. Rechargez la page, puis réessayez.',
  NOT_ACCEPTABLE: 'Un problème technique empêche cette action. Rechargez la page, puis réessayez.',
  UNSUPPORTED_MEDIA_TYPE: 'Ce type de fichier n’est pas accepté.',
  NOT_A_PDF: 'Ce fichier n’est pas un PDF. Choisissez un fichier au format PDF.',
  FILE_TOO_LARGE: 'Ce fichier est trop volumineux. Choisissez un PDF plus léger.',
  DUPLICATE_CODE: 'Ce code existe déjà dans ce chantier. Choisissez-en un autre.',
  DUPLICATE_REVISION_LABEL: 'Cet indice existe déjà pour ce document. Choisissez-en un autre.',
  REVIEW_ALREADY_OPEN: 'La relecture de cette révision est déjà ouverte.',
  EMAIL_ALREADY_USED: 'Cette adresse e-mail est déjà utilisée.',
  NO_REVIEWERS: 'Choisissez au moins un relecteur.',
  UNKNOWN_STATUS: 'Ce statut n’existe pas ou n’est plus proposé. Choisissez-en un autre.',
  PARTICIPANT_NOT_IN_PROJECT: 'Cet intervenant ne fait pas partie de ce chantier.',
  UNEXPECTED_REVIEWER: 'Ce relecteur n’est pas attendu sur cette révision.',
  REQUEST_REFUSED:
    'La demande a été refusée. Réessayez, et si le problème persiste, contactez l’administrateur.',
  INTERNAL_ERROR: 'Un problème est survenu de notre côté. Réessayez dans un instant.',
  NETWORK_ERROR: 'Le serveur est injoignable. Vérifiez votre connexion, puis réessayez.',
  UNEXPECTED_RESPONSE: 'Le serveur a répondu de façon inattendue. Réessayez dans un instant.',
}

/** Why one field was refused. Kept short: it sits right under the field. */
export const FIELD_MESSAGES: Record<FieldErrorCode, string> = {
  REQUIRED: 'Ce champ est obligatoire.',
  TOO_LONG: 'Ce texte est trop long.',
  TOO_SHORT: 'Ce texte est trop court.',
  TOO_SMALL: 'Cette valeur est trop petite.',
  TOO_LARGE: 'Cette valeur est trop grande.',
  INVALID_EMAIL: 'Saisissez une adresse e-mail complète, par exemple nom@exemple.fr.',
  INVALID_FORMAT: 'Ce format n’est pas reconnu.',
  INVALID: 'Cette valeur n’est pas acceptée.',
}

type PasswordReasonMessage = string | ((limit: number | undefined) => string)

/** Why a new password was refused. A length reason carries the bound the server applied. */
export const PASSWORD_REASON_MESSAGES: Record<WeakPasswordReason, PasswordReasonMessage> = {
  EMPTY: 'Saisissez un mot de passe.',
  TOO_SHORT: (limit) =>
    limit === undefined
      ? 'Ce mot de passe est trop court.'
      : `Le mot de passe doit comporter au moins ${limit} caractères.`,
  TOO_LONG: (limit) =>
    limit === undefined
      ? 'Ce mot de passe est trop long.'
      : `Le mot de passe ne doit pas dépasser ${limit} caractères.`,
  SAME_AS_EMAIL: 'Le mot de passe ne doit pas être votre adresse e-mail.',
  CONTAINS_NAME: 'Le mot de passe ne doit contenir ni votre identifiant, ni le nom du service.',
  COMMON: 'Ce mot de passe est trop courant. Choisissez une phrase que personne d’autre n’utilise.',
  TOO_REPETITIVE: 'Ce mot de passe est trop répétitif. Variez les caractères.',
  SAME_AS_CURRENT: 'Le nouveau mot de passe doit être différent de l’actuel.',
}

/** The message to show for any failure. Never the server's own text. */
export function errorMessage(error: unknown): string {
  if (!isApiError(error)) return ERROR_MESSAGES.UNEXPECTED_RESPONSE
  if (error.code === 'WEAK_PASSWORD' && error.reason !== undefined) {
    return passwordReasonMessage(error.reason, error.limit)
  }
  return ERROR_MESSAGES[error.code]
}

export function fieldMessage(code: FieldErrorCode): string {
  return FIELD_MESSAGES[code]
}

export function passwordReasonMessage(reason: WeakPasswordReason, limit: number | undefined): string {
  const message = PASSWORD_REASON_MESSAGES[reason]
  return typeof message === 'function' ? message(limit) : message
}
