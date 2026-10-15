# Events

Everything that happens is written to the append-only journal (`audit_event`), in the same
transaction as the change it describes. This is the catalogue.

An event has: a **type**, an **actor**, a **target** (type + id), a small **payload** of text
values and the **time it occurred**. Payloads never contain a token, a password, a hash of either,
a file name, or a reviewer's free-text comment.

## Actors

| Actor | Meaning |
|---|---|
| `user:<id>` | A signed-in contractor user |
| `reviewer:<id>` | A reviewer acting through their link |
| `system:bootstrap`, `system:admin-command` | Account creation from configuration |
| the typed name | When an opinion is entered on behalf: who typed it |

## Revision (target `REVISION`)

| Type | Actor | Payload | When |
|---|---|---|---|
| `RevisionSubmitted` | user | `documentId`, `label`, `sequence`, `sha256` | A PDF was deposited |
| `ReviewOpened` | user | `expectedReviewers` | The review was opened for a revision |
| `ReviewLinkIssued` | user | `reviewerId`, `linkId`, `expiresAt` | A link was issued (revokes the previous one) |
| `ReviewLinkOpened` | reviewer | `linkId` | The reviewer's page was used (first real gesture, `POST /r/{token}/seen`); merely loading it records nothing |
| `ReviewLinkRevoked` | user | `reviewerId` | A link was revoked |
| `ReviewLinkRejected` | reviewer | `reason` (`EXPIRED` or `REVOKED`), `linkId` | A known link that could no longer be used was presented |
| `DocumentViewed` | reviewer | `linkId`, `sha256` | The plan was actually fetched |
| `OpinionGiven` | reviewer, or the typist | `statusCode`, `effect`, plus `onBehalfOf` when entered on behalf | An opinion was recorded |
| `SupersededRevisionAnswered` | reviewer | `sequence` | An answer arrived on a revision that has a newer one |

An unknown token leaves **no** event (there is no revision to attach it to, and recording it would
let anyone fill the journal).

## Project (target `PROJECT`)

| Type | Payload |
|---|---|
| `ProjectCreated` | `name` |
| `WorkPackageAdded` | `code` |
| `ParticipantAdded` | `participantId`, `role` |
| `ParticipantDeactivated` | `participantId` |
| `StatusDefined` | `code`, `effect` |
| `StatusDeactivated` | `code` |
| `DefaultReviewersSet` | `workPackage`, `count` |
| `MemberGranted` | `userId` |
| `WaitingThresholdsSet` | `chaseFromDays`, `lateFromDays` |

## Document (target `DOCUMENT`)

| Type | Payload |
|---|---|
| `DocumentCreated` | `projectId` |

## User (target `USER`)

| Type | Actor | Payload |
|---|---|---|
| `UserCreated` | creator | `mustChangePassword` |
| `LoginSucceeded` | `user:<id>` | none |
| `LoginFailed` | `user:<id>` | none (only for an existing account) |
| `PasswordChanged` | `user:<id>` | none |
| `PasswordHashUpgraded` | `user:<id>` | none (an older hash was replaced at sign-in) |

## Reading the journal

`GET /api/projects/{projectId}/revisions/{revisionId}/history` returns the events of a revision,
oldest first, to a member of the project. Nothing else is exposed.

## Adding an event

1. Write it in the same transaction as the change.
2. Keep the payload to short, non-secret text values (`JsonPayload.of(...)` escapes them).
3. Add it here.
