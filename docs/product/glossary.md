# Glossary

Domain terms used on site (French) and the names used in code. Add a term here
before inventing a new name.

| French term | Code name | Meaning |
|---|---|---|
| Projet / chantier | `Project` | A construction site with its documents and participants |
| Lot | `WorkPackage` | A trade or part of the work (for example Lot 01, Lot 02, GENERAL) |
| Document | `Document` | An execution document: drawing, calculation note, method statement, data sheet |
| Révision / indice | `Revision` | One version of a document, labelled by its index (A, B, C, REV1...) |
| Intervenant | `Reviewer` | A project participant who gives opinions |
| Rôle | `ReviewerRole` | `ARCHITECT`, `ENGINEERING_OFFICE`, `CONTROL_OFFICE` |
| Intervenant attendu | expected reviewer | A reviewer the revision was sent to, whose opinion is expected |
| Avis / visa | `Opinion` | A reviewer's answer on one revision: a status and a comment |
| Statut d'avis (VSO, VAO, VPI, NC, REF) | `OpinionStatus` | Configurable per project, each one has an effect |
| Effet d'un statut | `OpinionEffect` | `APPROVING`, `APPROVING_WITH_RESERVES`, `INFORMATIVE`, `BLOCKING` |
| Statut global | `RevisionStatus` | Derived from opinions, never typed by hand |
| Revue d'une révision | `RevisionReview` | Who must answer, what they answered, and the resulting status |
| Lien de relecture | `ReviewLink` | Personal secure link sent to one reviewer for one revision |
| Journal d'événements | `AuditEvent` | Append-only record of every action |
| Entreprise | `Contractor` | The company submitting the documents |

## Status labels

| Code | French label | Effect |
|---|---|---|
| VSO | Visé sans observation | `APPROVING` |
| VAO | Visé avec observations | `APPROVING_WITH_RESERVES` |
| VPI | Visé pour information | `INFORMATIVE` |
| NC | Non conforme | `BLOCKING` |
| REF | Refusé | `BLOCKING` |

These are defaults taken from observed usage and still to be confirmed with the
end user. They are configuration, not code.

## Revision statuses

| Status | When |
|---|---|
| `PENDING` | At least one expected reviewer has not answered |
| `ACKNOWLEDGED` | Everyone answered, informative opinions only |
| `APPROVED` | Everyone answered, no blocking opinion, at least one approval |
| `APPROVED_WITH_RESERVES` | As above, with at least one reserve to lift |
| `TO_REVISE` | At least one blocking opinion |
