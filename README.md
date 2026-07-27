# Blaie

Blaie is an authenticated productivity application that turns unstructured input into structured Inbox items.
The current production path accepts text or voice, persists the text Capture workflow durably, classifies it
asynchronously, and exposes the result through the web Inbox. Image capture remains planned work.

## Current architecture

- `blaie_fe`: Next.js 16, React 19, TypeScript, TanStack Query, Axios, Vitest and Testing Library.
- `blaie_be`: Java 25, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway and Spring Modulith.
- PostgreSQL is authoritative for users, captures, processing jobs, Inbox items, outbox publications and audit
  events.
- Redis provides rate limiting, provider-concurrency coordination and an at-least-once wake-up stream. It is not
  the source of truth for capture work and never transports raw Capture text.
- The AI provider is selected through an application port; the current adapter integrates DeepSeek for text
  classification.

The backend is a modular monolith with top-level modules:

- `auth`: local authentication, Google OAuth, cookie/token lifecycle, verification and password reset.
- `authz`: permission enrichment and permission-plus-ownership decisions.
- `capture`: text/voice ingestion, durable processing, AI classification, Capture item writes and admin job operations.
- `inbox`: owner-scoped read-only queries over completed `capture_items`.
- `audit`: privacy-safe access audit capture and admin audit queries.
- `retention`: bounded scheduled cleanup for expired operational records.
- `configuration`: application-wide security composition.
- `core`: shared request context, errors, cursors, rate limiting, security primitives and observability.

## Implemented user flow

1. The web client authenticates with HttpOnly `blaie_at` and `blaie_rt` cookies.
2. Unsafe cookie requests send the `XSRF-TOKEN` value as `X-XSRF-TOKEN`.
3. `POST /api/v1/captures/text` includes an `Idempotency-Key`.
4. PostgreSQL atomically stores the Capture, processing job and transactional outbox publication.
5. The outbox listener publishes bounded identifiers to Redis.
6. A worker claims the durable job, applies content/PII policy, calls the classifier and stores terminal results.
7. The frontend tracks processing Captures, resolves uncertain submissions and refreshes the Inbox.

Every Capture/Inbox operation requires an explicit regular-user permission and remains scoped to the authenticated
owner. Admin operations require the server-side admin role and dedicated permissions. Audit events contain safe
identifiers and outcomes, never raw Capture/provider/outbox content.

## Run locally

Backend:

```powershell
cd blaie_be
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Frontend:

```powershell
cd blaie_fe
npm install
npm run dev
```

## Verify

```powershell
cd blaie_be
.\mvnw.cmd test

cd ..\blaie_fe
npm run lint
npm test
npm run build
```

Backend integration tests use Docker/Testcontainers for PostgreSQL and Redis. The latest full backend verification
after the Inbox module split passed 317 tests.

## Documentation

- API conventions: `docs/system_design/api_contract/00.overview.txt`
- Capture API: `docs/system_design/api_contract/02.capture.txt`
- Inbox API: `docs/system_design/api_contract/03.inbox.txt`
- Admin API: `docs/system_design/api_contract/12.admin.txt`
- Component architecture: `docs/system_design/architecture/3.component.txt`
- Capture privacy policy: `docs/system_design/privacy/capture-data-policy.txt`
- Operations runbook: `docs/runbooks/capture-processing.md`
- Backend agent context: `blaie_be/agents/`
- Frontend agent context: `blaie_fe/agents_context/`
