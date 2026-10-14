# ADR 0006: Web interface stack and how it is served

- Status: Accepted
- Date: 2026-10-14
- Relates to: [ADR 0001](0001-stack.md), [ADR 0003](0003-identity-sessions-and-authorization.md)

## Context

Two audiences use the interface. The contractor works on a desktop all day (a dense follow-up table,
filters, forms). A reviewer opens a link once, from any device, reads a PDF and answers. There are
no accounts for reviewers, and the link is the credential.

The API already exists and is the contract (`docs/api/openapi.json`). The interface must not widen
the attack surface: no third-party scripts, no token in any URL the browser sends elsewhere.

## Decision

1. **Stack**: React with TypeScript, built by Vite. shadcn/ui components on Tailwind CSS for the
   interface, TanStack Table for the follow-up table, PDF.js to show the plan inside the page.
   The client code is generated from the OpenAPI contract so the two cannot drift.
2. **Same origin**. The backend serves the built interface. No CORS, session cookies stay
   `SameSite` and first-party, and the content security policy can be `'self'`.
3. **Two kinds of paths**:
   - `/relecture/{token}` is the page a reviewer's link opens; `/r/{token}/**` is the review API it calls;
   - `/connexion`, `/projets/**` are contractor screens; `/api/**` is the contractor API.
4. **The shell holds no secret.** Every interface route answers with the same static entry page.
   Authorization happens on the API, never in the page. Anything the backend does not recognise is
   still refused by the default chain.
5. **Reviewer page**: served by the reviewer filter chain, so it inherits rate limiting, `no-store`
   and `Referrer-Policy: no-referrer`. The page reads the token from its own address and does not
   keep it anywhere else (no storage, no analytics, no external request).
6. **Content security policy** on every page: scripts only from our origin, no plugins, no framing,
   `connect-src 'self'`. Inline styles are allowed because of the component library; PDF.js needs a
   worker from a blob.
7. **French first**, accessible by default (keyboard, contrast, visible focus); responsive for the
   reviewer page, desktop-first for the contractor screens.

## Consequences

- One deployable artifact, one origin to secure.
- The front-end build output is copied into the backend's static resources; for development, Vite's
  dev server proxies `/api` and `/r` to the backend.
- A change to the API fails the contract test first, then the generated client at compile time.
- Not chosen: server-rendered pages (the follow-up table and PDF viewer are interaction-heavy) and a
  separately hosted front end (CORS, cross-site cookies, a second origin to defend).
