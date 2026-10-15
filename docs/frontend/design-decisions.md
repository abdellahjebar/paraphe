# Design decisions

Decisions that shape the interface, with the evidence behind each one.

**Status: provisional.** Nothing here has been tested with end users yet. Each row says how strong
its evidence is and what would change it.

| Level | Meaning |
|---|---|
| **Measured** | Checked on the files or computed by a script |
| **Standard** | Required or recommended by a published standard or guideline |
| **Judgement** | Reasoned choice, no direct evidence |
| **To test** | Needs real devices or real users |

## Principles

| # | Decision | Evidence |
|---|---|---|
| 1 | French only for now. Texts are centralised so another language can be added. | Judgement |
| 2 | Responsive from 320 to 2560 px; no two-dimensional scrolling at 320 px, except for the data table itself. | Standard (WCAG 1.4.10) |
| 3 | The reviewer page is designed mobile first; the follow-up and set-up screens desktop first. Both work everywhere. | Judgement |
| 4 | Colour never carries a state alone: colour, icon shape and label together. | Standard (WCAG 1.4.1) |
| 5 | Formal register ("vous") everywhere. | Judgement |
| 6 | Light theme by default, dark theme chosen by the user. | Standard (NN/g dark mode) |

## Typography

| # | Decision | Evidence |
|---|---|---|
| 7 | Source Sans 3, self-hosted, WOFF2, weights 400, 600 and 700. | Measured (46 KB, SIL OFL 1.1) |
| 8 | System font stack as fallback, with `size-adjust` to avoid a visible jump on load. | Standard (web.dev) |
| 9 | Tabular figures: the font ships them by default. | Measured |
| 10 | Base size 15 px in the follow-up, 16 px or more on the reviewer page. | Judgement (x-height 7.3 px, against 6.8 px for the 11 pt font of the current spreadsheet) |
| 11 | Line height 1.5 for running text, line length about 70 characters. | Standard (WCAG 1.4.12), judgement |
| 12 | Ordinary no-break space (U+00A0) before `:` `;` `!` `?` and inside numbers. | Measured (U+202F is absent from the subset) |
| 13 | Codes and tokens are never set in a font where "I" and "l" look alike. | Measured |

## Colour

| # | Decision | Evidence |
|---|---|---|
| 14 | Monochrome palette with cool greys; the primary button is ink black; colour is kept for states. | Judgement |
| 15 | Text at 4.5:1 or more; field borders, buttons and focus at 3:1 or more. | Measured (280 checks, none failing), Standard (WCAG 1.4.3, 1.4.11) |
| 16 | Eight states: approved, approved with reserves, pending, to revise, acknowledged, not sent, to chase, late. | Judgement, vocabulary to confirm |
| 17 | "To chase" is outlined, "late" is filled, so they differ even without colour. | Judgement |
| 18 | Status codes (VSO, VAO, REF) stay visible next to the label. | Judgement |
| 19 | Real borders and SVG icons so the interface survives forced-colours mode. | Standard (MDN), to test |

## Structure and components

| # | Decision | Evidence |
|---|---|---|
| 20 | Standard density by default, compact on request. | Judgement |
| 21 | The follow-up opens on "to handle", grouped by document; the full table is one click away. | Judgement |
| 22 | Table: readable first column, frozen header and first column, visible filters. | Standard (NN/g) |
| 23 | Entering an answer on behalf of a reviewer uses a side panel, not a modal. Modals are for confirmations. | Standard (NN/g) |
| 24 | Targets: 56 px for the reviewer's choices, 44 px for buttons, never below 24 px. | Standard (WCAG 2.5.8, Android 48 dp) |
| 25 | Sticky headers must not hide the focused element (`scroll-padding`). | Standard (WCAG 2.4.11) |

## Reviewer page and trust

| # | Decision | Evidence |
|---|---|---|
| 26 | An unusable link gets an honest but non-specific message (expired, replaced or incomplete). The server answer stays identical. | Judgement |
| 27 | Each link comes with a ready-made message, and copying the bare link is always possible. | Judgement, to validate |
| 28 | The page shell is neutral: no document or company name in the title or in link previews. | Judgement, to test |
| 29 | PDF: capped resolution, rendered on demand, with an "open in the phone's viewer" fallback. | To test on a large real plan |
| 30 | No external font or script; strict content security policy. | Standard |

## Sending

| # | Decision | Evidence |
|---|---|---|
| 31 | Paraphe sends the e-mails itself, with a dated proof of sending (ADR 0007 to come). | Judgement |
| 32 | Copy the link, copy the message, share on WhatsApp from the contractor's own number. | Judgement |
| 33 | No WhatsApp Business API for now. | Judgement |

## Forms and wording

| # | Decision | Evidence |
|---|---|---|
| 34 | Labels are visible, above the field, short, sentence case, no colon. A placeholder never replaces a label. | Standard (GOV.UK text input) |
| 35 | Hint text only when most people need it: one short sentence, no link. | Standard (GOV.UK) |
| 36 | Optional fields are marked "(facultatif)"; required fields are not marked. | Judgement |
| 37 | Validation on submit. After a first error, fields are re-checked as the person corrects them. Nothing the person typed is cleared. | Standard (GOV.UK error message), judgement |
| 38 | Errors are listed in a summary at the top and repeated at the field, with the same words. A hidden "Erreur :" prefix is read by screen readers. | Standard (GOV.UK) |
| 39 | An error says what went wrong and how to fix it, reusing the words of the label. No "veuillez", no "invalide", no "une erreur est survenue". | Standard (GOV.UK), adapted to French |
| 40 | No `type="number"`; `inputmode` for numbers; `spellcheck="false"` for e-mail addresses and codes; no `maxlength` that blocks typing (a counter instead). | Standard (GOV.UK) |
| 41 | `autocomplete` on every field that has a known purpose; paste and password managers are never blocked. | Standard (WCAG 1.3.5, 3.3.8) |
| 42 | Password: show/hide control, no composition rules, no periodic change. | Standard (NIST SP 800-63B-4) |
| 43 | Buttons say what they do ("Envoyer mon avis", "Se connecter"); one primary button per screen; a visible loading state; the action cannot be sent twice. | Judgement |
| 44 | Destructive confirmations name the object and the consequence. | Judgement |
| 45 | Dates are written jj/mm/aaaa, shown in the site's time zone and stored in UTC. | Judgement |
| 46 | A recorded answer is confirmed inline and stays visible; a toast alone is never the only confirmation. | Judgement |
| 47 | The interface never shows a backend message: it maps stable error codes to French wording. | Judgement |

## Backend gaps found while checking forms

| Gap | Standard | Planned action |
|---|---|---|
| Minimum password length is 12; a password used alone should have at least 15. | NIST SP 800-63B-4 | Decide and raise, with a test |
| No check against lists of common or compromised passwords. | NIST SP 800-63B-4 | Add a blocklist check |
| The 64-character maximum can be reached by 72 bytes first with accented characters, so a permitted length may be refused. | NIST: allow at least 64 characters | Pre-hash before bcrypt, or move to a memory-hard hash |
| Validation messages are English text. | Decision 47 | Add stable error codes to the API contract |

## Rejected

| Rejected | Reason |
|---|---|
| Inter | Proportional figures by default; "I" and "l" nearly identical |
| A blue accent | The primary button and the "pending" state would both be blue |
| Warm stone neutrals | Too close to the amber of the attention states |
| Right-click menu as the only path | Invisible, absent on touch |
| Full calendar widget | No screen needs it yet |
| Decorative animated effects | No relation to a tool whose value is proof |

## Still open

| Question | How to settle it |
|---|---|
| Format and weight of the real plans | A real plan, anonymised |
| Rendering on real Windows, Android and iOS devices | Device tests |
| Direct sunlight, dark theme in practice | Tests in situ |
| Reaction of the first user and two reviewers | Observation session |
| Domain name for outbound e-mail | Owner decision, before the e-mail step |
