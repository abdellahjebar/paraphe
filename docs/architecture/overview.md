# Architecture overview

How the system is built today. Decisions and their reasons are in the [ADRs](adr/).

## Shape

One Spring Boot application (a modular monolith), one PostgreSQL database, one directory of files.

```
 Browser (to build)               Reviewer, no account
 cookie session + CSRF            personal link
        │ /api/**                         │ /r/{token}/**
        ▼                                 ▼
 ┌──────────────────────────────────────────────────────────────┐
 │  Security: three filter chains (contractor / reviewer / deny) │
 ├──────────────────────────────────────────────────────────────┤
 │  Controllers (adapters)  →  use cases  →  domain (plain Java) │
 ├──────────────────────────────────────────────────────────────┤
 │  JDBC adapters                            Local file storage  │
 └───────────────┬────────────────────────────────┬─────────────┘
                 ▼                                ▼
            PostgreSQL 17                  files on disk (PDF)
```

## Modules

| Module | Responsibility | Depends on |
|---|---|---|
| `shared` | Types every module needs and that depend on nothing | nothing |
| `audit` | The append-only journal | nothing |
| `reviews` | Expected reviewers, opinions, derived status, review links | `audit` |
| `projects` | Projects, work packages, participants, statuses, membership | `reviews`, `audit` |
| `documents` | Documents, immutable revisions, file storage | `projects`, `reviews`, `audit` |
| `submission` | Operations that span modules; the dashboard | all of the above |
| `identity` | Accounts, sessions, security configuration | `projects`, `audit`, `shared` |
| `api` | Global error mapping, OpenAPI header | the modules' exceptions |

The arrows only go down. `ArchitectureTest` fails the build on a cycle, on a domain that touches a
framework, on a use case that touches an adapter, and on one module using another's adapters.

## Inside a module

```
<module>/
├── domain/          plain Java: entities, value objects, rules. No framework.
├── port/            interfaces the use cases need (repositories, file storage...)
├── application/     use cases, transactions, exceptions
└── infrastructure/  adapters: JDBC, web controllers, local disk
```

## A request, end to end

`POST /api/projects/{p}/documents/{d}/revisions` (send a revision):

1. The `/api/**` chain checks the session and the CSRF token.
2. `@PreAuthorize` checks the caller is a member of project `p`; if not, the answer is "not found".
3. The controller checks that document `d` belongs to `p`.
4. `SubmissionService.submitForReview`, in one transaction:
   decides the reviewers (explicit, or the work package's defaults) **before** storing anything;
   stores the PDF and fingerprints it; creates the revision; opens the review; issues one link per
   reviewer; writes the events.
5. If any step fails, the transaction rolls back and the stored file is removed.
6. The links come back once, with their tokens.

## Reading the dashboard

Four fixed SQL queries read facts (documents with their newest revision, expected reviewers,
opinions, links). The status of each revision is then derived by the domain's `RevisionReview`, so
the rules exist in one place only.

## Data

Nine migrations (`V1`..`V9`). Evidence tables (`audit_event`, `opinion`, `revision`, `review_link`)
refuse modification at the database level. Foreign keys, including composite ones, carry
the cross-project invariants. See [domain-model.md](../domain/domain-model.md).

## What does not exist yet

- The web interface.
- A restricted database role for the application (today it owns the schema and could drop a trigger).
- Deployment, backups, monitoring.
- Notifications and reminders.
- Import of the contractor's existing spreadsheet.

## Limits to remember

- Sessions and rate limiters are in memory: a single instance.
- Files are on local disk: backups and a move to object storage are to design.
- No pagination: sized for hundreds of documents per project.
