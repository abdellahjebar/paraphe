import { useEffect, useState } from 'react'
import { applyTheme, readTheme, saveTheme, type Theme } from '@/lib/theme/theme'

// Tailwind only generates classes that appear in full in the source, so no class name is built from a variable.
const SURFACES = [
  ['background', 'bg-background text-foreground'],
  ['card', 'bg-card text-card-foreground'],
  ['muted', 'bg-muted text-foreground'],
  ['primary', 'bg-primary text-primary-foreground'],
  ['destructive', 'bg-destructive text-destructive-foreground'],
] as const
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

/** A page to look at the theme in both modes. Temporary: it goes away when the real screens exist. */
export function ThemeCheck() {
  const [theme, setTheme] = useState<Theme>(() => readTheme())

  useEffect(() => {
    // #dark lets a screenshot be taken without touching the stored choice.
    if (window.location.hash === '#dark') applyTheme('dark')
  }, [])

  function choose(next: Theme) {
    setTheme(next)
    saveTheme(next)
  }

  return (
    <main className="mx-auto max-w-5xl space-y-10 px-4 py-8">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold">Paraphe</h1>
          <p className="text-muted-foreground">Contrôle du thème « Encre révisée »</p>
        </div>
        <div role="group" aria-label="Thème" className="flex gap-2">
          {(['light', 'dark', 'system'] as const).map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={theme === value}
              onClick={() => choose(value)}
              className="min-h-11 rounded-md border border-input px-4 font-semibold aria-pressed:bg-primary aria-pressed:text-primary-foreground"
            >
              {value === 'light' ? 'Clair' : value === 'dark' ? 'Sombre' : 'Système'}
            </button>
          ))}
        </div>
      </header>

      <section aria-labelledby="t-type" className="space-y-3">
        <h2 id="t-type" className="text-xl font-semibold">
          Typographie
        </h2>
        <p className="text-2xl font-semibold">Plan de poutres R+1</p>
        <p className="text-muted-foreground">Lot 01 · Structure · Indice C</p>
        <p className="max-w-prose">
          « À reprendre : l&apos;épaisseur de la dalle haute ne correspond pas à la note de calcul. Voir
          œuvre, façade nord, coupe A-A. »
        </p>
        <p className="font-semibold">
          STR-EXE-PLN-0142-C&nbsp;&nbsp;14/10/2026&nbsp;&nbsp;9 jours d&apos;attente
        </p>
        <p className="text-2xl font-semibold">Il1 | O0 | rn m | S5 | B8</p>
      </section>

      <section aria-labelledby="t-colors" className="space-y-3">
        <h2 id="t-colors" className="text-xl font-semibold">
          Surfaces
        </h2>
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          {SURFACES.map(([name, classes]) => (
            <li key={name} className={`rounded-lg border border-border p-4 text-sm font-semibold ${classes}`}>
              {name}
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="t-states" className="space-y-3">
        <h2 id="t-states" className="text-xl font-semibold">
          États
        </h2>
        <ul className="flex flex-wrap gap-3">
          {STATES.map(([label, classes]) => (
            <li key={label} className={`rounded-full px-3 py-1 text-sm font-semibold ${classes}`}>
              {label}
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="t-form" className="max-w-sm space-y-3">
        <h2 id="t-form" className="text-xl font-semibold">
          Champ et bouton
        </h2>
        <label htmlFor="demo" className="block font-semibold">
          Adresse e-mail
        </label>
        <input
          id="demo"
          type="email"
          autoComplete="username"
          className="min-h-12 w-full rounded-md border-2 border-input bg-background px-3"
        />
        <button
          type="button"
          className="min-h-11 w-full rounded-md bg-primary px-4 font-semibold text-primary-foreground"
        >
          Se connecter
        </button>
        <p>
          Un <a href="#t-form">lien</a> dans un texte.
        </p>
      </section>
    </main>
  )
}
