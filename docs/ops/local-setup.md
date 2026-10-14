# Run it locally

## Prerequisites

- **JDK 25** (set `JAVA_HOME`). A Microsoft or Temurin build both work.
- **Docker Desktop**, running. It provides PostgreSQL for development and for the tests.

Maven is not needed: the wrapper (`./mvnw`) downloads it.

## Run the tests

```
cd backend
./mvnw -B test
```

296 tests. The first run pulls the PostgreSQL image, so it takes a few minutes. Tests start their own
database containers; nothing needs to be running beforehand.

Without Docker you can still run the domain tests:

```
./mvnw -B -Dtest=RevisionReviewTest test
```

## Run the application

```
cd backend
docker compose up -d                    # PostgreSQL 17 on 127.0.0.1:5433, creates both roles
export PARAPHE_BOOTSTRAP_EMAIL=you@example.org
export PARAPHE_BOOTSTRAP_PASSWORD='a long passphrase of your own'
./mvnw spring-boot:run
```

On the first start, with no account in the database, the account above is created. The password is
read, hashed and never printed. Leaving the variables in place on later starts is harmless.

Check it is up:

```
curl http://localhost:8080/actuator/health
```

## The two database roles

| Role | Used by | Can |
|---|---|---|
| `paraphe` (owner) | Flyway, at startup | Create and change the schema |
| `paraphe_app` | The running application | Only what migration `V10` grants: it cannot change or delete the evidence, drop a trigger, or alter the schema |

`docker compose up` creates `paraphe_app` on the first start of an empty database, from
`backend/db/create-app-role.sql`. See [ADR 0005](../architecture/adr/0005-database-roles.md).

**If you already have a local database volume from before this change**, it has no
`paraphe_app` role and migration `V10` will stop with a message saying so. Either recreate the
database (this erases its data):

```
docker compose down -v
docker compose up -d
```

or create the role once, by hand:

```
docker exec -i paraphe-postgres psql -U paraphe -d paraphe < db/create-app-role.sql
```

## Add a second account

An account created by an administrator must choose its own password at first sign-in. Start the
application once with:

```
--paraphe.admin.create-user.email=colleague@example.org
--paraphe.admin.create-user.password='a temporary passphrase'
--paraphe.admin.create-user.display-name='Colleague'
--paraphe.admin.create-user.projects=<project-id>,<another-project-id>
```

then remove the arguments.

## Settings you may need

| Setting | Default | Purpose |
|---|---|---|
| `PUBLIC_BASE_URL` | `http://localhost:8080` | Prefix of the review links given to the contractor |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` behind HTTPS |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | local values, user `paraphe_app` | The **application's** connection (restricted role) |
| `MIGRATION_DATABASE_URL`, `MIGRATION_DATABASE_USER`, `MIGRATION_DATABASE_PASSWORD` | local values, user `paraphe` | The **owner's** connection, used only by Flyway |
| `OPENAPI_ENABLED` | `false` | Serve `/v3/api-docs` (tests and local tooling only) |
| `paraphe.storage.root` | `./data/files` | Where PDFs are kept (ignored by git) |
| `paraphe.storage.max-file-size-bytes` | 26214400 | Upload limit |
| `paraphe.review-link.ttl-days` | 30 | Lifetime of a review link |

Both local database passwords are development defaults and must never be used elsewhere. Elsewhere,
change the application role's password (`ALTER ROLE paraphe_app PASSWORD '...'`) and give the owner's
credentials only to the migration step.

## Changing the API

Routes are described by `docs/api/openapi.json`, generated from the code. After changing a route:

```
./mvnw -B -Dtest=OpenApiContractTest -Dopenapi.update=true test
```

then review the diff of that file.

## Troubleshooting

- **`docker` not found in a shell**: Docker Desktop installs under
  `%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin`, which some shells do not have on `PATH`.
- **Port 5433 already used**: change the mapping in `backend/compose.yaml` and `DATABASE_URL`.
- **A test fails to start**: check Docker Desktop is running before anything else.
