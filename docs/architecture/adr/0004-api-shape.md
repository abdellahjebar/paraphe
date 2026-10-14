# ADR 0004: Shape of the HTTP API

- Status: Accepted
- Date: 2026-10-14
- Relates to: [ADR 0003](0003-identity-sessions-and-authorization.md)

## Context

The API is the only door to the product. It serves two audiences with opposite trust levels (a
signed-in contractor and an anonymous reviewer holding a link) and it must not become the place
where the rules of the product are forgotten or weakened.

## Decision

1. **Routes are nested under the project**: `/api/projects/{projectId}/documents/{documentId}/…`,
   `/api/projects/{projectId}/revisions/{revisionId}/…`. Authorization is then uniform: one guard on
   the project id.
2. **A resource is always checked against the project in the URL.** A document or revision of
   another project is "not found". This closes the guessed-identifier hole that flat routes
   (`/api/revisions/{id}`) leave open.
3. **Missing and forbidden look the same**: 404, same body. 403 is reserved for CSRF and for an
   account that must change its password.
4. **The guard cannot be forgotten**: a test walks every registered route and fails the build when
   a project route lacks `@PreAuthorize("@projectAccessGuard.isMember(#projectId, authentication)")`,
   or when an `/api` route appears outside the known prefixes.
5. **The reviewer API is deliberately narrow**: three routes under `/r/{token}`. A reviewer's
   answer is acknowledged with "recorded" and "revision superseded", never with the global status,
   the other reviewers or any identifier of the project.
6. **Errors are standard problem bodies** produced by one handler. Unexpected failures are a plain
   500 with a correlation id; their detail stays in the server log.
7. **The contract is generated and committed**: springdoc produces it, `docs/api/openapi.json`
   holds the reviewed copy, and `OpenApiContractTest` fails on any difference. The endpoint that
   serves it is off unless explicitly enabled.
8. **Unknown JSON fields are rejected**, so a typo fails loudly.
9. **Review links are returned once**, in the response that issues them, as a full URL built from
   `PUBLIC_BASE_URL`.
10. **Same origin, no CORS**: the web application is served beside the API (a proxy in development).

## Alternatives considered

- **Flat routes with the project implied by the session**: shorter URLs, but a user in several
  projects makes "the current project" ambiguous and moves authorization into hidden state.
- **Returning 403 for a project that exists**: more informative, and it tells an attacker which
  identifiers are real.
- **Hand-written OpenAPI**: drifts from the code the first time someone forgets.
- **GraphQL**: more flexibility than a first version with a handful of screens needs.

## Consequences

- URLs are longer, and the front end carries the project id everywhere.
- Every controller method repeats the same guard annotation. That repetition is the point: it is
  visible in review and enforced by a test.
- Changing a route is a visible change in `openapi.json`, which is exactly when it should be
  discussed.
- A multipart upload sits behind the project guard and a size limit, and an oversized file is a 413
  before it is stored.
