# Interface components

Which components the interface is built from, where each one comes from, and what was changed.
The reasoning behind the choices is in [design-decisions.md](design-decisions.md) and
[ADR 0006](../architecture/adr/0006-web-interface.md).

## Principle

One base of primitives, and for each precise need one chosen building block. Never two complete component
libraries side by side.

| Need | Source |
|---|---|
| Primitives (button, field, dialog, menu, tabs...) | shadcn/ui, Base UI variant, copied into `frontend/src/components/ui/` |
| Blocks adapted from a third party | `frontend/src/components/blocks/`, with a provenance header |
| Our own components (status badge, reviewer cell, PDF viewer...) | `frontend/src/components/app/` |
| Table | TanStack Table, dressed with the primitives |
| Forms | React Hook Form and Zod, with the `Field` primitive |
| Icons, notifications | Lucide, Sonner |

A third-party component is never used directly in a screen: it goes through `blocks/` or `app/`, so that its
adaptation and its provenance stay visible.

## Adoption grid

A component enters the repository only if it passes this grid.

| Criterion | Requirement |
|---|---|
| Licence | Compatible with a public repository; the text was read, not assumed |
| Network | No request to another origin (font, image, script): the content security policy would block it |
| Dependencies | None added without a reason; weight measured |
| Accessibility | Keyboard, visible focus, accessible name, contrast, checked with axe |
| Consistency | Uses theme tokens, never a colour written in a component |
| Language | No text written in a component; texts come from the French message file |
| Provenance | Source, licence, date and changes recorded here |
| Decision | Adopted, adapted or rejected, with the reason |

## Register

Source for all rows: shadcn/ui `shadcn@4.21.4`, Base UI variant (`base-nova`), MIT. Added 2026-10-16.

| Component | Decision | Adapted | Note |
|---|---|---|---|
| Button | Adopted | Sizes (44 px by default, 36 px small, 48 px large), full-opacity focus ring | Decisions 15 and 24 |
| Input | Adopted | Height 44 px | Border uses `--input` (3:1) |
| Label | Adopted | Larger and bolder text | Lint rule requires `htmlFor` or nesting at every use |
| Field | Adopted | `FieldError` has the hidden "Erreur :" prefix, and no `role="alert"`: the summary at the top announces, a second alert would read everything twice | Decision 38 |
| Card | Adopted | none | |
| Alert | Adopted | none | Carries `role="alert"`: use it only for real warnings |
| Badge | Adopted | Height 24 px, text size | Generic; the status badge is our own component |
| Avatar | Adopted | none | |
| Separator | Adopted | none | |
| Skeleton | Adopted | none | Hide it from assistive technology when it is only decoration |
| Spinner | Adopted | none | Announce the busy state where it matters (decision 48) |
| Alert Dialog | Adopted (Base UI) | none | Used for the session warning: focus inside, returns to the page on close; Escape means "I am here" |
| Sonner (toaster) | Adopted | Follows our theme module instead of a Next.js theme package | A toast is never the only confirmation (decision 46) |

## Blocks

| Block | Decision | Note |
|---|---|---|
| `login-05` (shadcn registry, read in full on 2026-10-16) | **Layout only** | It is an e-mail only form with "Continue with Apple" and "Continue with Google" buttons, a "Sign up" link, a terms notice, the `cn` package and an internal icon component. None of that is kept: the page is a centred card with our own fields, texts and behaviour. |

## Our own components

| Component | Where | What it does |
|---|---|---|
| ErrorSummary | `components/app/` | Lists what went wrong at the top of a form, takes the focus on each failed attempt, links to each field |
| PasswordInput | `components/app/` | A password field with a show/hide control; never blocks pasting |
| LoginForm | `features/auth/` | The sign-in form: checked when sent, nothing cleared, one request at a time |
| ChangePasswordForm | `features/auth/` | The mandatory first password change: no confirmation field (the password can be shown), the server says why a password is refused |
| SessionWatcher | `features/auth/` | Keeps the session alive while the person works, warns two minutes before the end with a countdown, follows the other tabs |

## What the command-line tool added that we did not keep

| Added by the tool | What we did | Why |
|---|---|---|
| `cn` package | Own `cn()` in `src/lib/utils.ts` (clsx and tailwind-merge) | An extra, recently repurposed dependency for two lines of code |
| `next-themes` | Own theme module in `src/lib/theme` | A Next.js library, and we already had a theme store |
| `@fontsource-variable/geist` and a Geist font rule | Removed | The interface font is Source Sans 3, self-hosted |
| `shadcn` package as a dependency | Its stylesheet is copied to `src/styles/shadcn-base.css` | The package brings a large toolchain, and 7 high audit findings (`braces`), to ship one CSS file |
| A block of colour variables overriding the theme | Removed | The theme "Encre révisée" is the only source of colours |

A test (`src/dependency-policy.test.ts`) and a lint rule fail the build if one of these packages comes back.

## Adding a component

```
cd frontend
set npm_config_before=<seven days ago, ISO date>     # never install a version younger than a week
set npm_config_ignore_scripts=true
npx shadcn@<version> add <component>
npm run ui:normalize      # brings the file in line with the rules above
npm run format
npm run lint && npm test
```

Then read the generated file, apply the adoption grid, and add a row to the register above.
Anything with a text, a colour or a size written in it is adapted before it is used.
