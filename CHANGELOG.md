# Changelog

All notable changes. Format follows Keep a Changelog; versions are not tagged yet.

## Unreleased

### Added

- **Review domain**: expected reviewers, opinions, status derived from the effect of each opinion.
- **Audit journal**: append-only, refused at database level (update, delete, truncate).
- **Projects**: work packages, participants with role and specialty, configurable statuses with an
  effect, default reviewers per work package, project membership.
- **Documents**: documents, immutable revisions with a SHA-256 fingerprint, PDF storage behind a port.
- **Review links**: 256-bit tokens stored hashed, expiry, revocation, regeneration.
- **Submission**: send a revision atomically (file, revision, review, links), answer through a link,
  view the plan through a link, record an answer on behalf of a reviewer.
- **Identity**: accounts without public sign-up, bcrypt, sessions, CSRF, lockout, rate limiting,
  forced password change for new accounts, three separate security filter chains.
- **HTTP API**: contractor routes under `/api`, reviewer routes under `/r/{token}`, dashboard,
  history, global error mapping, generated and committed OpenAPI contract.
- **Waiting thresholds**: per project, when to chase and when a wait is late (defaults 8 and 15
  days); the follow-up screen carries an attention level per reviewer cell.
- **Web interface hosting**: the backend serves the built interface and its routes, with a strict
  content security policy; a reviewer's link opens `/relecture/{token}`.
- **Tests**: 323, including architecture rules, an authorization matrix, a guard-coverage check,
  attack tests against the database role and a full review cycle over real HTTP.
- **Documentation**: glossary, domain model, event catalogue, architecture overview, API
  conventions, security model, ADRs 0001 to 0006, local setup.

### Security

- The application connects as a restricted database role that owns nothing; migrations run as the
  owner. The role cannot update, delete or truncate the evidence tables, drop or disable a trigger,
  alter the schema, create roles or switch triggers off for its session. Proven by attack tests.
- The framework's default generated password is no longer printed at startup.
- Error bodies never repeat the request path, which on a review link is the token.
- A project or resource the caller may not use is indistinguishable from one that does not exist.

### Not yet

- Web interface, spreadsheet import, deployment, backups, pilot.
