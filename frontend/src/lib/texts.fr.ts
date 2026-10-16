/*
 * The words of the interface, in French, formal register. Components and screens take their texts from here
 * (never written inside a component), so a text is changed in one place and the whole interface can be read
 * in one go. Error texts are in messages.fr.ts.
 *
 * A no-break space (U+00A0) goes before ":" ";" "!" "?" and inside numbers (decision 12).
 */
export const COMMON = {
  productName: 'Paraphe',
  showPassword: 'Afficher le mot de passe',
  /** Read by screen readers before an inline error, so the error is not announced as plain text. */
  errorPrefix: 'Erreur : ',
  errorSummaryTitle: 'Certains champs sont à corriger',
  loading: 'Chargement en cours',
} as const

export const LOGIN = {
  pageTitle: 'Connexion',
  title: 'Connexion',
  description: 'Saisissez votre adresse e-mail et votre mot de passe.',
  emailLabel: 'Adresse e-mail',
  passwordLabel: 'Mot de passe',
  submit: 'Se connecter',
  submitting: 'Connexion en cours',
  emailRequired: 'Saisissez votre adresse e-mail.',
  passwordRequired: 'Saisissez votre mot de passe.',
  failureTitle: 'Connexion impossible',
} as const
