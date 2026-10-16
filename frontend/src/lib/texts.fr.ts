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

export const CHANGE_PASSWORD = {
  pageTitle: 'Choisissez votre mot de passe',
  title: 'Choisissez votre mot de passe',
  description:
    'Votre compte vient d’être créé. Pour continuer, remplacez le mot de passe provisoire par le vôtre.',
  currentLabel: 'Mot de passe provisoire',
  newLabel: 'Nouveau mot de passe',
  newHint: 'Au moins 15 caractères. Une phrase facile à retenir convient très bien.',
  currentRequired: 'Saisissez votre mot de passe provisoire.',
  newRequired: 'Saisissez votre nouveau mot de passe.',
  submit: 'Enregistrer et continuer',
  submitting: 'Enregistrement en cours',
  failureTitle: 'Le mot de passe n’a pas été modifié',
} as const

export const SESSION = {
  checking: 'Vérification de votre connexion en cours',
  errorTitle: 'Impossible de vérifier votre connexion',
  retry: 'Réessayer',
  ended: 'Votre session est terminée. Connectez-vous à nouveau.',
  signedOut: 'Vous avez été déconnecté.',
  signOut: 'Se déconnecter',
  signingOut: 'Déconnexion en cours',
} as const

export const NOT_FOUND = {
  pageTitle: 'Page introuvable',
  title: 'Cette page n’existe pas',
  text: 'L’adresse est peut-être incomplète, ou la page a été déplacée.',
  back: 'Retourner à l’accueil',
} as const

/** Placeholders for the screens that arrive with the next steps. */
export const COMING = {
  projectsTitle: 'Mes chantiers',
  reviewTitle: 'Relecture',
  text: 'Cet écran arrive bientôt.',
} as const

export const SESSION_WARNING = {
  title: 'Votre session va se terminer',
  description:
    'Pour votre sécurité, la session se ferme après un moment sans activité. Elle se terminera dans',
  stay: 'Rester connecté',
  signOut: 'Se déconnecter',
} as const
