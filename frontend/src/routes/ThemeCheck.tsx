import { InfoIcon, TriangleAlertIcon } from 'lucide-react'
import { useEffect } from 'react'
import { toast } from 'sonner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldDescription, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Toaster } from '@/components/ui/sonner'
import { applyTheme, saveTheme, type Theme } from '@/lib/theme/theme'
import { useThemeChoice } from '@/lib/theme/useTheme'

// Tailwind only generates classes that appear in full in the source, so no class name is built from a variable.
const STATES = [
  ['Approuvé', 'bg-status-approved-soft text-status-approved'],
  ['Approuvé avec réserves', 'bg-status-reserves-soft text-status-reserves'],
  ['En attente', 'bg-status-pending-soft text-status-pending'],
  ['À reprendre', 'bg-status-revise-soft text-status-revise'],
  ['Pris acte', 'bg-status-info-soft text-status-info'],
  ['Non envoyé', 'bg-status-neutral-soft text-status-neutral'],
  ['À relancer', 'bg-status-chase-soft text-status-chase ring-1 ring-status-chase'],
  ['En retard', 'bg-status-late-soft text-status-late'],
] as const

const CHOICES: ReadonlyArray<readonly [Theme, string]> = [
  ['light', 'Clair'],
  ['dark', 'Sombre'],
  ['system', 'Système'],
]

/** A page to look at the theme and the base components in both modes. Temporary: it goes away with the real screens. */
export function ThemeCheck() {
  const theme = useThemeChoice()

  useEffect(() => {
    // #dark lets a screenshot be taken without touching the stored choice.
    if (window.location.hash === '#dark') applyTheme('dark')
  }, [])

  return (
    <main className="mx-auto max-w-5xl space-y-10 px-4 py-8">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold">Paraphe</h1>
          <p className="text-muted-foreground">Contrôle du thème et des composants de base</p>
        </div>
        <div role="group" aria-label="Thème" className="flex gap-2">
          {CHOICES.map(([value, label]) => (
            <Button
              key={value}
              type="button"
              variant={theme === value ? 'default' : 'outline'}
              aria-pressed={theme === value}
              onClick={() => saveTheme(value)}
            >
              {label}
            </Button>
          ))}
        </div>
      </header>

      <section aria-labelledby="c-type" className="space-y-3">
        <h2 id="c-type" className="text-xl font-semibold">
          Typographie
        </h2>
        <p className="text-2xl font-semibold">Plan de poutres R+1</p>
        <p className="text-muted-foreground">Lot 01 · Structure · Indice C</p>
        <p className="max-w-prose">
          « À reprendre : l&apos;épaisseur de la dalle haute ne correspond pas à la note de calcul. Voir
          œuvre, façade nord, coupe A-A. »
        </p>
        <p className="text-2xl font-semibold">Il1 | O0 | rn m | S5 | B8</p>
      </section>

      <section aria-labelledby="c-states" className="space-y-3">
        <h2 id="c-states" className="text-xl font-semibold">
          États (composant à venir : StatusBadge)
        </h2>
        <ul className="flex flex-wrap gap-3">
          {STATES.map(([label, classes]) => (
            <li key={label} className={`rounded-full px-3 py-1 text-sm font-semibold ${classes}`}>
              {label}
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="c-buttons" className="space-y-3">
        <h2 id="c-buttons" className="text-xl font-semibold">
          Boutons et pastilles
        </h2>
        <div className="flex flex-wrap items-center gap-3">
          <Button>Se connecter</Button>
          <Button variant="outline">Annuler</Button>
          <Button variant="secondary">Copier le lien</Button>
          <Button variant="ghost">Voir</Button>
          <Button variant="destructive">Révoquer le lien</Button>
          <Button disabled>
            <Spinner /> Envoi en cours
          </Button>
          <Button variant="link">Mot de passe</Button>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Badge>Badge</Badge>
          <Badge variant="secondary">Secondaire</Badge>
          <Badge variant="outline">Contour</Badge>
          <Badge variant="destructive">Destructif</Badge>
          <Avatar>
            <AvatarFallback>KL</AvatarFallback>
          </Avatar>
          <Button type="button" variant="outline" onClick={() => toast.success('Avis enregistré')}>
            Afficher une notification
          </Button>
        </div>
      </section>

      <section aria-labelledby="c-form" className="grid gap-6 md:grid-cols-2">
        <h2 id="c-form" className="sr-only">
          Formulaire
        </h2>
        <Card>
          <CardHeader>
            <CardTitle>Connexion</CardTitle>
            <CardDescription>Saisissez votre adresse e-mail et votre mot de passe.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <Field>
              <FieldLabel htmlFor="check-email">Adresse e-mail</FieldLabel>
              <Input id="check-email" type="email" autoComplete="username" spellCheck={false} />
            </Field>
            <Field data-invalid="true">
              <FieldLabel htmlFor="check-password">Mot de passe</FieldLabel>
              <Input
                id="check-password"
                type="password"
                autoComplete="current-password"
                aria-invalid="true"
              />
              <FieldDescription>15 caractères au minimum</FieldDescription>
              <FieldError>Le mot de passe doit comporter au moins 15 caractères.</FieldError>
            </Field>
          </CardContent>
          <Separator />
          <CardFooter>
            <Button className="w-full">Se connecter</Button>
          </CardFooter>
        </Card>

        <div className="space-y-4">
          <Alert>
            <InfoIcon />
            <AlertTitle>Ces liens ne seront plus affichés</AlertTitle>
            <AlertDescription>Copiez-les maintenant : ils ne peuvent pas être retrouvés.</AlertDescription>
          </Alert>
          <Alert variant="destructive">
            <TriangleAlertIcon />
            <AlertTitle>Ce lien n&apos;est plus valable</AlertTitle>
            <AlertDescription>
              Il a peut-être expiré, avoir été remplacé par un lien plus récent, ou ne pas être complet.
            </AlertDescription>
          </Alert>
          <div className="space-y-2" aria-hidden="true">
            <Skeleton className="h-6 w-2/3" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-5/6" />
          </div>
        </div>
      </section>

      <Toaster />
    </main>
  )
}
