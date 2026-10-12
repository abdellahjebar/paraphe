# Paraphe

Track the approval of construction execution documents without a spreadsheet.

## The problem

On a construction site, a contractor must get its execution documents
(drawings, calculation notes, method statements) approved by several parties
before work can start: the architect, the engineering office, the control
office. Today this is often tracked in an Excel file that circulates by email
or WhatsApp. The result is multiple versions of the file, no proof of who
opened what and when, manual follow-ups, and approvals that do not match the
latest revision of a drawing.

## What Paraphe does

- The contractor uploads a document and sends each reviewer a personal,
  secure link. Reviewers do not need an account.
- Each reviewer gives an opinion (for example approved, approved with
  comments, rejected) on a specific revision of the document.
- The global status of a document is derived from the opinions, never typed
  by hand.
- Every action (link sent, document opened, opinion given) is recorded in an
  append-only, timestamped audit trail.
- A dashboard shows the state of every document at a glance.

## Design principles

- **Proof first.** The audit trail is append-only: events are never edited or
  deleted.
- **An opinion belongs to a revision**, never to a document, so an approval can
  never apply to the wrong version of a drawing.
- **Status is derived**, not typed: a document is approved because every
  required opinion is positive.
- **Configurable vocabulary.** Statuses and document types are defined per
  project, not hard-coded.
- **Zero friction for reviewers.** A link is enough, no account needed.

## Roadmap

| Increment | Goal |
|---|---|
| MVP 1 | Replace the Excel sheet: upload, secure links, opinions, dashboard, audit trail |
| MVP 2 | Revisions (index A, B, C) with full history, reminders |
| MVP 3 | Exports for site meetings, multiple projects, annotations on drawings |

Details in [docs/product/roadmap.md](docs/product/roadmap.md).

## Status

Early design phase, no code yet. The product is designed around the real
workflow of a contractor on a heritage restoration site in Morocco.

## Documentation

| Topic | File |
|---|---|
| Vision and scope | [docs/product/vision.md](docs/product/vision.md) |
| Glossary (French domain terms and code names) | [docs/product/glossary.md](docs/product/glossary.md) |
| Architecture and decisions | [docs/architecture/](docs/architecture/) |

## Authors

| Name | Role |
|---|---|
| JEBAR Abdellah | Software design and development |
| LAFRID Khawla | Civil engineering, domain expertise and product validation |

## License

Copyright © 2026 JEBAR Abdellah, LAFRID Khawla. All rights reserved.
