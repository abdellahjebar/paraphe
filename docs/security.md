# Security

What Paraphe protects, from whom, and which test proves each protection. Decisions are in
[ADR 0003](architecture/adr/0003-identity-sessions-and-authorization.md).

## What matters most

1. **The proof**: who answered what on which revision, and when, must not be alterable.
2. **Documents**: construction plans are confidential and must reach only the people they were sent to.
3. **Review links**: whoever holds one can answer for a reviewer, so a link is a credential.

## Who can do what

| Actor | Gets in with | Can |
|---|---|---|
| Contractor | Account and password, cookie session | Manage the projects they are a member of |
| Reviewer | Personal link, no account | See one revision and answer on it, nothing else |
| Anyone else | Nothing | Read the health probe |

## Threats and protections

| Threat | Protection | Proven by |
|---|---|---|
| Guessing a review token | 256 random bits; only the SHA-256 is stored; same answer for unknown, expired and revoked | `ReviewLinkDomainTest`, `ReviewLinkServiceIntegrationTest` |
| Hammering the public links | Per-address rate limit, 429 with `Retry-After` | `SecurityIntegrationTest.reviewerPathsAreRateLimitedPerAddress` |
| A token leaking through logs | Loggers kept at INFO; no access log; tokens and hashes never printed | `LogHygieneTest`, `ReviewLinkServiceIntegrationTest.theTokenIsNeverStoredOnlyItsHash` |
| A token leaking through error pages | Request path removed from every error body | `SecurityIntegrationTest.errorBodiesNeverEchoTheLinkToken` |
| A token leaking through `Referer` | `Referrer-Policy: no-referrer` | `SecurityIntegrationTest.reviewerResponsesAreNeverCached...` |
| A cached review page | `Cache-Control: no-store` on review paths | same test |
| Guessing a password | bcrypt cost 12, 5 failures lock account and address for 15 minutes | `IdentityServiceIntegrationTest`, `SecurityIntegrationTest` |
| Finding which emails have accounts | Same response and same time for unknown account and wrong password; lock behaves the same | `wrongPasswordAndUnknownAccountAreIndistinguishable`, `anUnknownAccountLocksLikeARealOne...` |
| Stolen database | Passwords only as bcrypt hashes; tokens only as SHA-256 | `createdAccountStoresOnlyAHashOfThePassword` |
| Session fixation | Session id replaced at sign-in | `signingInReplacesTheSessionIdentifier` |
| Session cookie theft by script | `HttpOnly`, `Secure` in production, `SameSite=Lax` | `SessionCookieHttpTest` |
| Cross-site request forgery | CSRF token (cookie + header) on all `/api/**` writes, including sign-in | `stateChangingCallWithoutACsrfTokenIsRefused` |
| Using a project one is not in | Membership check; denial is "not found" | `aMemberSeesTheirProjectAndAnOutsiderSeesNothing`, `anOutsiderGetsNotFoundOnEveryProjectRoute`, `anOutsiderGetsNotFoundOnEveryDocumentAndReviewRoute` |
| Guessing identifiers across projects (IDOR) | A document or revision is always checked against the project in the URL; mixed identifiers answer like a missing one | `aDocumentOfAnotherProjectIsNotFoundWhateverItsIdentifier`, `aRevisionOfAnotherProjectCannotBeDownloaded` |
| Forgetting the guard on a new route | A test walks every registered route and fails the build | `ApiGuardCoverageTest` |
| A reviewer learning about the others | The reviewer API returns only the document, the statuses and their own answer; no global status, no other reviewer, no project identifier | `ReviewerApiIntegrationTest.reviewerSeesTheDocumentAndTheStatusesTheyMayGiveAndNothingElse`, `reviewerAnswersAndIsToldOnlyThatItWasRecorded` |
| Telling why a link failed | Unknown, expired and revoked links give byte-identical answers | `unusableLinksAllGiveTheSameAnswerWhateverTheReason` |
| A reviewer answering for someone else | The identity comes from the token, never from the request body; unknown fields are rejected | `unknownFieldsInTheAnswerAreRejected`, `aReviewerCannotUseTheirLinkToAnswerForSomeoneElse` |
| An answer passed off as the reviewer's own | On-behalf entries carry `enteredBy` and the typist as journal actor | `contractorCanEnterAnAnswerOnBehalfAndItIsMarkedAsSuch` |
| Internal detail in an error | One handler; unexpected failures are a plain 500 with a correlation id | `ApiErrorHandlingTest` |
| The contract exposing the surface to the public | `/v3/api-docs` is closed unless explicitly enabled; test-only routes are excluded | `theApiContractIsNotServedByDefault`, `OpenApiContractTest` |
| A cookie set on a reviewer route | Reviewer routes are stateless | `reviewerRoutesNeverSetCookiesOrKeepASession` |
| End to end regression | The whole cycle over real HTTP, cookies and CSRF | `FullReviewCycleTest` |
| A fresh account used as is | Must change password before anything else | `anAccountThatMustChangeItsPasswordCanDoNothingElse...` |
| Rewriting history | **Two independent locks.** The application's database role holds no UPDATE, DELETE or TRUNCATE privilege on the journal, opinions, revisions and links; and database triggers refuse the same operations even for the owner | `DatabaseRolesIntegrationTest`, `JdbcAuditLogIntegrationTest`, `ReviewServiceIntegrationTest`, `DocumentServiceIntegrationTest`, `ReviewLinkServiceIntegrationTest` |
| A compromised application dropping or disabling the triggers | The application role owns nothing: `DROP TRIGGER`, `DISABLE TRIGGER`, `ALTER TABLE`, `DROP TABLE` all answer "must be owner" | `itCannotDropATrigger`, `itCannotDisableTriggers`, `itCannotChangeTheSchema` |
| Switching triggers off for a session | `session_replication_role` is refused to a non-superuser | `itCannotBypassTriggersByClaimingToBeAReplica` |
| Granting itself more | A `GRANT` by the application role grants nothing | `tryingToGrantItselfAPrivilegeGrantsNothing` |
| A new table nobody decided about | The test lists every table with its exact privileges and fails on a new one | `everyTableHasAnExplicitDecision`, `theRestrictedRoleHoldsExactlyTheTablePrivilegesListed`, `theRestrictedRoleCanUpdateExactlyTheColumnsListed` |
| A default password in the log | The framework's generated default user is disabled | `noDefaultPasswordIsPrintedAtStartup` |
| An opinion from someone not asked | Domain check **and** composite foreign key | `ReviewServiceIntegrationTest` |
| Path tricks in uploads | Keys generated by storage, file name sanitised, PDF recognised by content | `LocalFileStorageTest`, `DocumentFileNameTest` |
| A non-PDF or oversized upload | Content signature check, size limit, nothing kept on refusal | `DocumentServiceIntegrationTest`, `DocumentSizeLimitIntegrationTest` |
| Dangerous external link | Only `http` and `https` accepted | `DocumentsDomainTest` |
| Leaving files behind after a failure | Cleanup on rollback | `SubmissionAtomicityIntegrationTest` |

## Known limits

| Limit | Consequence | When it matters |
|---|---|---|
| Sessions and rate limiters are in memory | Single instance; reset on restart | Before running more than one instance |
| The database owner (migrations) can still drop the triggers | A compromised **application** cannot; a compromised **owner** can. The owner's credentials must be kept out of the running application's reach | At deployment: give the owner credentials only to the migration step, and rotate the application role's password (`ALTER ROLE paraphe_app PASSWORD ...`) |
| Account lockout can be used to lock a known email out for 15 minutes | Availability, not confidentiality | If it becomes a nuisance, lock by address first |
| The client address is the socket address | Behind a proxy every caller looks the same until forwarding is configured | At deployment |
| A reviewer's link can be forwarded | Whoever holds it can answer | Mitigated by expiry, revocation and the per-link journal; reviewer accounts are out of scope |
| **Never set `org.springframework.security`, `org.springframework.web` or the Tomcat loggers to DEBUG in production** | They print request paths, which are review tokens | Always |

## Rules for contributors

- Never log, return or store a review token, a password or a hash. Their `toString` is masked on
  purpose; do not add formatting that bypasses it.
- A new endpoint under `/api/**` that takes a project id carries the `@PreAuthorize` guard.
- A new table that holds evidence gets the append-only triggers in the same migration.
- Test data is synthetic. Real site documents never enter the repository.
