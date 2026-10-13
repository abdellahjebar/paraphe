# ADR 0002: Persistence, module boundaries and file serving

- Status: Accepted
- Date: 2026-10-13
- Amends: [ADR 0001](0001-stack.md) (persistence row and module-boundary tooling)

## Context

ADR 0001 planned Spring Data JPA for aggregates and Spring Modulith for module
boundaries. Building the first modules showed neither is needed yet:

- The review and audit modules were written with explicit SQL, because they hold
  append-only data where "never UPDATE or DELETE" must be provable.
- The remaining modules (projects, documents) are small, and their rules live in
  the domain, not in an object-relational mapping.
- Modules are organised by layer (`domain`, `port`, `application`,
  `infrastructure`), a layout Spring Modulith's package-level rules do not fit
  without extra annotations.

Phase 3 also has to decide how files are served to reviewers who have no account.

## Decision

1. **Persistence is plain JDBC (`JdbcClient`) everywhere.** Spring Data JPA is
   removed. Reintroduce it only if a module genuinely benefits from it, with its
   own ADR.
2. **Module boundaries are verified with ArchUnit**, in `ArchitectureTest`:
   - `domain` depends on no framework and no other layer;
   - `application` never depends on `infrastructure`;
   - modules are free of cycles;
   - a module never uses another module's `infrastructure`.
3. **Files are served by the application itself**, after checking the reviewer's
   token, not through signed URLs. A document is stored on disk behind a
   `FileStorage` port. Signed URLs return only if storage moves to an object store.
4. **A revision is submitted as an uploaded PDF**, with an optional external link
   (for example a Drive link) kept as a reference. Uploading is what makes
   "document opened" traceable.
5. **A review link is scoped to one revision and one reviewer**, with one active
   link at a time. Default lifetime 30 days, to be confirmed with the end user.

## Consequences

- Fewer moving parts and no object-relational mapping to reason about when
  proving immutability.
- Every query is written by hand: more SQL, fewer surprises. The dashboard
  queries benefit from that.
- The boundary rules are a test, so a violation fails the build.
- Serving files through the application means it sits on the download path. Fine
  at this scale; revisit with object storage.
- Reviewers must open the application to see a plan, instead of staying in their
  usual tool. This is a deliberate trade for traceability and is the main
  adoption risk, to be validated during the pilot.
