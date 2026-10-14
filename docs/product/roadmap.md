# Roadmap

## MVP 1: replace the spreadsheet

Goal: a contractor runs a project, sends revisions, gets answers and sees the state, with proof.

| Capability | State |
|---|---|
| Accounts and sessions, no public sign-up | done |
| Projects, work packages, participants with role and specialty | done |
| Configurable statuses with an effect, default reviewers per work package | done |
| Documents and immutable revisions (PDF) | done |
| Send a revision to chosen or default reviewers, atomically | done |
| Personal review links: hashed token, expiry, revocation, regeneration | done |
| Reviewer page, plan download, answer; reviewers see nothing else | done |
| Status derived from opinions; only expected reviewers can be late | done |
| Answer entered on behalf of a reviewer, marked as such | done |
| Dashboard with filters, waiting days and link state | done |
| Append-only journal and per-revision history | done |
| Generated and committed API contract | done |
| Restricted database role for the application | **to do** |
| Web interface (follow-up screen, send, reviewer page) | **to do** |
| Import of the existing spreadsheet | **to do** |
| Deployment, backups | **to do** |
| Pilot on one real project | **to do** |

## MVP 2: stay on top of it

| Capability | Why |
|---|---|
| Reminders and due dates per reviewer | Today nobody is chased automatically |
| Reserves to lift, tracked until closed | "Approved with comments" is only the start of the work |
| Attachments to observations | Reviewers return annotated files |
| Archiving of documents | A site accumulates hundreds of them |
| "Not concerned" answer from a reviewer | To be confirmed with the contractor |
| Sending links by e-mail | WhatsApp copy-paste first |

## MVP 3: share and scale

| Capability | Why |
|---|---|
| Exports for site meetings (PDF, spreadsheet) | The contractor must still show it to others |
| Several projects overview | One contractor runs several sites |
| Annotations on the plan | Remarks tied to a place |
| Accounts for reviewers (optional) | Only if links prove insufficient |

## Not planned

DWG or BIM handling, billing, a mobile app, reviewing the content of a plan.

## Open questions with the end user

Listed in the local research notes: who must answer for which work package, when the architect is
required, the exact meaning of the "informative" and "non conforme" statuses, expected response
times, and whether reviewers will accept to leave their usual tool for a link.
