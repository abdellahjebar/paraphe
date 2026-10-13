# ADR 0003: Identity, sessions and authorization

- Status: Accepted
- Date: 2026-10-14
- Relates to: [ADR 0002](0002-persistence-boundaries-and-files.md)

## Context

Two kinds of people use Paraphe and they must not be treated alike:

- the **contractor**, who owns projects and sends documents, signs in and works in a session;
- **reviewers**, who answer one revision through a personal link and have no account.

The product's value is proof. An attacker who can sign in as the contractor, read a project they do
not belong to, or guess a review link undermines it. A review link's path is its credential, so
anything that repeats a request path (logs, error bodies, `Referer`) can leak it.

## Decision

1. **Accounts** live in `app_user`. There is **no public sign-up**: the first account comes from
   configuration while none exists, further ones from an administrator command, and a new account
   must choose its own password before doing anything else.
2. **Passwords** are hashed with bcrypt at cost 12. A cost below 10 is refused at startup unless
   weak hashing is explicitly allowed, which only the test configuration does. The policy is about
   length (12 to 64 characters, at most 72 bytes) rather than character classes.
3. **Sign-in** gives the same answer for an unknown account and a wrong password, and spends the same
   time on both. Failures are counted per account and per client address (5 in 15 minutes) and lock
   both; an unknown account locks like a real one, so lockout reveals nothing.
4. **Sessions** are server-side, in memory, in a `HttpOnly`, `SameSite=Lax` cookie that is `Secure`
   in production. The session id is replaced at sign-in.
5. **Three filter chains**: `/r/**` (reviewers: no session, no CSRF because the token is the
   credential, rate limited, `no-store`, `no-referrer`), `/api/**` (contractor: session, CSRF
   cookie + header, JSON errors), and everything else refused apart from the health probe.
6. **Authorization** is project membership (`project_member`). Endpoints that take a project id are
   guarded with `@PreAuthorize("@projectAccessGuard.isMember(#projectId, authentication)")`. A
   denial is answered as "not found", never "forbidden", so a project one cannot use is
   indistinguishable from one that does not exist.
7. **Error bodies never repeat the request path**: `instance` is always removed and `detail` is
   removed on `/r/**`, and Spring's `path` error attribute is dropped. Security and web loggers stay
   at INFO because their DEBUG output prints request paths.

## Alternatives considered

- **JWT in the browser**: stateless, but cannot be revoked on sign-out or password change without
  extra machinery, and is easier to leak from script-readable storage. A server session suits one
  application and one instance.
- **A single shared contractor account**: simpler, but the journal would say "admin" for every
  action, which defeats the proof.
- **Reviewer accounts**: out of scope for MVP 1. The link model does not prevent adding them.
- **Argon2**: slightly stronger, but needs an extra cryptography dependency for no practical gain at
  this scale.

## Consequences

- Sessions and the two rate limiters are in memory: valid for a **single instance**, lost on
  restart (which only relaxes limits and signs people out). Running several instances needs a shared
  store, to be decided then.
- Locking by account lets someone lock a known email out for 15 minutes. That is an accepted
  trade-off against guessing, and the lock expires by itself.
- The client address is the socket address. Behind a reverse proxy the forwarded header must be
  configured explicitly, and only for a proxy that is trusted.
- Whoever turns Spring's security or web logging to DEBUG in production would log review tokens.
  `docs/security.md` records this so it is never done blindly.
