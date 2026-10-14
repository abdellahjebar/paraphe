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
- **Tests**: 296, including architecture rules, an authorization matrix, a guard-coverage check and a
  full review cycle over real HTTP.
- **Documentation**: glossary, domain model, event catalogue, architecture overview, API
  conventions, security model, ADRs 0001 to 0004, local setup.

### Security

- Error bodies never repeat the request path, which on a review link is the token.
- A project or resource the caller may not use is indistinguishable from one that does not exist.

### Not yet

- Restricted database role for the application, web interface, spreadsheet import, deployment.
