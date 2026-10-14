# API conventions

Rules every endpoint follows. The list of routes itself is generated from the code (OpenAPI) and is
never written by hand.

## Two audiences, two prefixes

| Prefix | Caller | Credential |
|---|---|---|
| `/api/**` | The contractor, in the web application | Cookie session + CSRF token |
| `/r/{token}/**` | A reviewer | The token in the path, nothing else |
| `/relecture/{token}` | A reviewer (page) | Serves the interface; the page then calls `/r/{token}/**` |

Everything else is refused, apart from the health probe `/actuator/health`.

## Authentication and CSRF (`/api/**`)

1. `POST /api/auth/login` with `{ "email", "password" }`. The response sets the session cookie.
2. Every response carries an `XSRF-TOKEN` cookie. Send its value back in the `X-XSRF-TOKEN` header
   on every `POST`, `PUT`, `PATCH` and `DELETE`, **including the login itself**.
3. `GET /api/auth/me` tells who is signed in, `POST /api/auth/logout` ends the session.
4. An account created by an administrator must call `POST /api/auth/password` before anything else;
   until then every other route answers `403`.

## Projects and "not found"

Every route under `/api/projects/{projectId}/…` requires membership of the project. A caller who is
not a member receives **404**, with exactly the body returned for a project that does not exist, so
nobody learns that a project they cannot use is there. A resource (document, revision) that does
not belong to the project in the URL is answered the same way.

A new route under `/api/projects/{projectId}` must carry
`@PreAuthorize("@projectAccessGuard.isMember(#projectId, authentication)")`.
`ApiGuardCoverageTest` fails the build when one does not.

## Errors

Every error is a standard problem body (`application/problem+json`, RFC 9457):

```json
{ "type": "about:blank", "title": "Conflict", "status": 409, "detail": "Work package 'Lot 01' already exists in this project" }
```

| Situation | Status |
|---|---|
| Not signed in | 401 |
| Missing or wrong CSRF token, or a password change still required | 403 |
| Resource missing, **or not the caller's** | 404 |
| Wrong HTTP method | 405 |
| Duplicate (work package, status, revision index, open review, email) | 409 |
| File too large | 413 |
| File is not a PDF | 415 |
| Malformed JSON, unknown field, failed validation, bad identifier | 400 |
| Valid JSON the domain refuses (unknown status, no reviewer, contact missing...) | 422 |
| Too many requests | 429, with `Retry-After` |
| Unexpected failure | 500, with a `correlationId` and **no other detail** |

Bodies never contain a stack trace, a SQL message, a password, a token, a hash, or the request path.
`instance` is never set, and `detail` is dropped whenever it would repeat the path.

## Payloads

- JSON, `camelCase`, UTF-8.
- Identifiers are UUIDs. Timestamps are ISO-8601 in UTC. Enumerations are upper-case strings.
- **Unknown fields are rejected** (400), so a typo fails loudly instead of being ignored.
- No pagination in the first version: a project has hundreds of documents, not millions. Add it with
  the first list that needs it, as a backwards-compatible query parameter.

## Uploading a revision

`POST /api/projects/{projectId}/documents/{documentId}/revisions`, `multipart/form-data`:

| Part | Required | Meaning |
|---|---|---|
| `file` | yes | The PDF. Recognised by its content, not its name or declared type |
| `label` | yes | The revision's index ("A", "REV1"), unique per document |
| `externalLink` | no | A reference link, `http` or `https` only |
| `reviewerIds` | no, repeatable | Who must answer. When absent, the document's work package defaults are used |

The response (201) carries the revision and **one link per reviewer**. If anything fails the whole
operation is undone: no revision, no review, no link, no file.

## The reviewer side

Three routes, no session, no CSRF: the token in the path is the credential.

| Route | Returns |
|---|---|
| `GET /r/{token}` | The document title, the revision index, whether it is superseded, the statuses that may be given, and the reviewer's own last answer |
| `GET /r/{token}/document` | The PDF |
| `POST /r/{token}/opinion` | `{ "recorded": true, "revisionSuperseded": … }` |

An unusable link is always the same `404`. The reviewer is never given the global status, other
reviewers, or any project identifier.

## The generated contract

`docs/api/openapi.json` lists every route and payload. It is generated from the code and checked by
`OpenApiContractTest`; a route change that is not reflected there fails the build. See
[the local setup](../ops/local-setup.md) for how to accept a change.

## Secrets leave once

A review link's token is returned **only** by the call that issues it. It cannot be read back later:
only its hash is stored. A lost link is replaced by issuing a new one, which revokes the old one.

## Evolution

Routes are not versioned yet. A breaking change introduces `/api/v2/…` beside the old routes; the
old ones are removed only after the web application stopped using them.

## Same origin, no CORS

The web application is served from the same origin as the API (a dev proxy in development), so no
cross-origin request is allowed. Adding an origin is a security decision and needs its own ADR.
