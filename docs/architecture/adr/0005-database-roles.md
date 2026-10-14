# ADR 0005: Separate database roles for migrations and for the application

- Status: Accepted
- Date: 2026-10-14
- Relates to: [ADR 0002](0002-persistence-boundaries-and-files.md), [ADR 0003](0003-identity-sessions-and-authorization.md)

## Context

The value of Paraphe is proof: who answered what on which revision, and when, unalterable.
Database triggers already refuse `UPDATE`, `DELETE` and `TRUNCATE` on the evidence tables.

But a trigger belongs to the table's owner, and the application connected as that owner. Anyone who
could run SQL with the application's credentials (an injection, a compromised dependency, a leaked
connection string) could `DROP TRIGGER` or `ALTER TABLE ... DISABLE TRIGGER`, rewrite history, and
re-enable it. The guarantee held against bugs, not against an attacker, which is the case it exists for.

## Decision

1. **Two roles.**
   - the **owner** (`paraphe` in development) creates and owns the schema and runs Flyway;
   - the **application role** (`paraphe_app`) is what the running application connects as.
2. **The application role owns nothing and is not privileged**: not a superuser, no `CREATEROLE`,
   no `CREATEDB`, no replication, no `BYPASSRLS`, no `CREATE` on the schema.
3. **Its privileges are an explicit list**, stated once in migration `V10` and re-checked by a test:

   | Tables | Privileges |
   |---|---|
   | `audit_event`, `opinion`, `revision` | `SELECT`, `INSERT` |
   | `review_link` | `SELECT`, `INSERT`, `UPDATE` of `first_opened_at` and `revoked_at` only |
   | `project` | `SELECT`, `INSERT`, `UPDATE` of `chase_from_days` and `late_from_days` only (migration `V11`) |
   | `review_request`, `work_package`, `document`, `project_member` | `SELECT`, `INSERT` |
   | `participant`, `opinion_status_definition` | `SELECT`, `INSERT`, `UPDATE` of `active` only |
   | `default_reviewer` | `SELECT`, `INSERT`, `DELETE` |
   | `app_user` | `SELECT`, `INSERT`, `UPDATE` of `password_hash`, `must_change_password`, `last_login_at`, `active` |

4. **The role is created outside the migrations**, by `backend/db/create-app-role.sql`, run by the
   database owner: a role is a cluster-level object, and creating one needs a privilege the
   migrator should not have in production. Migration `V10` fails with a clear message if the role
   is missing, rather than leaving the application without its grants.
5. **Two datasources.** `spring.flyway.*` uses the owner; `spring.datasource.*` uses the
   application role. Tests are built the same way, with the same script.
6. **Every new table needs a decision.** `DatabaseRolesIntegrationTest` lists the tables and the
   exact privileges; a table without an entry fails the build.
7. **The triggers stay.** They remain the second, independent lock: they refuse even the owner.

## Alternatives considered

- **Keep one role and trust the triggers.** Simple, and wrong against the threat that matters.
- **`ALTER DEFAULT PRIVILEGES` so new tables are granted automatically.** Convenient, but it turns
  "forgot to decide" into "allowed". Explicit grants make the omission fail loudly.
- **Row-level security or a separate schema for the evidence.** More machinery than the
  guarantee needs; privileges plus triggers already give two independent locks.
- **Create the role from a migration.** Needs `CREATEROLE` on the migrator, which is the opposite
  of least privilege.

## Consequences

- A fresh environment needs one extra step (the role script). Docker Compose does it
  automatically on first start; elsewhere it is a documented command.
- An **existing** development volume predates the role: recreate it (`docker compose down -v`) or
  run the script by hand once.
- The application cannot run migrations by itself. Deployment must run them as the owner, then
  start the application as the restricted role, with the owner's credentials kept away from it.
- Tests that prove the triggers connect as the owner (`OwnerJdbc`); tests that prove the
  privileges connect as the application. The two are different claims and both are tested.
- The owner remains powerful. Protecting its credentials (used only by the migration step,
  rotated, never present in the running application's environment) is an operational duty, listed
  in `docs/security.md`.
