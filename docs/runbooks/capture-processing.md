# Capture processing operations runbook

This runbook covers the durable asynchronous text-capture path:

`HTTP -> PostgreSQL capture/job/outbox -> Redis Stream -> worker -> AI provider -> PostgreSQL result`

PostgreSQL is the source of truth. Redis is a wake-up transport. Operational actions must preserve that boundary.

The management plane listens on `127.0.0.1:8081` by default. Production may override it with
`BLAIE_MANAGEMENT_ADDRESS` and `BLAIE_MANAGEMENT_PORT`, but that address must remain private and must not be
published as an application port. Prometheus scrapes `/actuator/prometheus`; health probes use
`/actuator/health/liveness` and `/actuator/health/readiness`.

## Safety rules

- Never query, copy, log, or return `captures.original_text`, provider payloads, access tokens, secrets, or
  `event_publication.serialized_event` during routine triage.
- Use the secured admin endpoints for mutations. The SQL in this document is read-only.
- Use bearer authentication for command-line admin mutations. Never paste a real token into a ticket, chat,
  committed script, shell history, or dashboard annotation.
- Record the alert, UTC time, admin identity, request ID, job ID and action taken. Persistent audit-log storage is
  deferred to the privacy/retention work, so the structured application log is the current operation record.
- Do not edit an already-applied Flyway migration and do not update `event_publication` manually.

## What healthy looks like

- `min(capture_observability_source_up{source="db"}) == 1`
- `min(capture_observability_source_up{source="redis"}) == 1`
- Queue depth fluctuates and the oldest queued age stays below the admission SLA (five minutes by default).
- `capture_job_duration_seconds_count{outcome="completed"}` continues increasing while active work exists.
- Outbox backlog normally returns to zero within the configured recovery window.
- Redis pending work drains; a transient pending record is normal while a worker owns it.
- Provider concurrency usage never exceeds its configured limit.

Global PostgreSQL/Redis gauges are observed by every app instance. Filter them to matching instances with
`source_up == 1`, then aggregate with `max`, not `sum`; otherwise a failed replica can retain a stale last-good
value. Treat `min by (source) (capture_observability_source_up)` as fleet health so one failed sampler remains
visible. Counters and timers describe work performed by individual instances and are aggregated with
`sum(rate(...))`.

## First response to any alert

1. Note the alert start time in UTC and save the dashboard time range.
2. Check the DB and Redis observability-source gauges. If either source is down, its dependent gauges may be stale.
3. Check queued/retry_wait/processing depth, queued age, active leases, completion rate, outbox backlog, Redis
   pending work and provider errors.
4. Search structured logs by MDC `requestId` (returned in `X-Request-ID` and shown as `correlationId` in the admin
   job response), `jobId`, `captureId` or `providerAttemptId`. Never search by raw user text.
5. Decide whether to keep accepting work, pause admission, or drain workers using the procedures below.
6. After recovery, keep the dashboard open for at least one retry/backoff window and confirm the backlog drains.

## Alert-specific triage

### Metrics target or observability sampler unavailable

- If the application target is absent, check process/container health and the private management endpoint route.
- If `source="db"` is down, check PostgreSQL connectivity and pool saturation. Job/outbox gauges are not trusted
  until the source recovers.
- If `source="redis"` is down, capture submit/retry rate limiting fails closed and job wake-up publication/consumption
  is unavailable. PostgreSQL jobs and outbox records remain durable.
- Do not make an unhealthy sampler look healthy by replacing failed values with zero.

### Oldest queued job or queue depth is high

- Compare queued, retry_wait and total processing depth with active leases and provider concurrency usage. Processing
  depth includes expired leases because those jobs still count against admission until recovery changes their state.
- If provider slots are full and completion continues, the system is slow but progressing. Pause admission when the
  oldest age approaches the configured admission threshold.
- If active leases are lower than processing depth, expired processing work is awaiting or failing recovery. If
  active leases are zero while queued work exists, confirm at least one worker and one recovery role are enabled.
- Check Redis pending/stream length and queued redispatch rate. Redis record loss should cause a later PostgreSQL
  reconciliation, not permanent job loss.

### No completions while active work exists

- Check DB/Redis sampler freshness first.
- Check worker logs, executor capacity, provider concurrency wait and provider error rate.
- Check for expired processing leases (`stuck=true`) and stale-recovery increments.
- If a provider outage is sustained, pause admission. Do not disable the concurrency limiter.

### Outbox publication is old

- `GET /api/v1/admin/outbox/summary` shows safe backlog metadata.
- Confirm Redis connectivity and that at least one publisher and one recovery role are enabled.
- The recovery scheduler resubmits old incomplete `TextCaptureQueuedEvent` publications automatically.
- Do not mark the publication complete or delete it. Doing so before a durable job is terminal can lose its wake-up
  path.

### Dead jobs increase

- Group the metric by `failure_class` and inspect safe job metadata through the admin API.
- `content_terminal` is usually input/policy behavior and must not be replayed unchanged.
- `provider_terminal` generally requires correcting provider configuration before requeue.
- `provider_retryable` suggests timeout, 429, 5xx or invalid output; confirm provider health before requeueing many
  jobs.
- `system_retryable` suggests worker/infrastructure behavior; inspect lease, recovery and Redis/DB health.

### Redis pending work grows

- Compare pending count, stream length, active leases and worker executor/provider capacity.
- A malformed record is acknowledged and deleted automatically by the worker.
- A valid duplicate is safe: PostgreSQL dispatch-generation and lease fencing reject a second execution.
- Do not run broad `XTRIM`, `DEL`, `XACK` or `XDEL` commands during normal triage. Escalate an exact-record removal
  only after proving its PostgreSQL job is terminal and recording the incident decision.

## Secured admin commands

The examples use a placeholder rather than a real credential:

```text
Authorization: Bearer <short-lived-admin-access-token>
```

List dead jobs:

```http
GET /api/v1/admin/jobs?status=dead&limit=50
```

List active jobs whose recovery timestamp is due:

```http
GET /api/v1/admin/jobs?stuck=true&limit=50
```

Inspect one job without returning user content:

```http
GET /api/v1/admin/jobs/{jobId}
```

Requeue one eligible dead job:

```http
POST /api/v1/admin/jobs/{jobId}/requeue
```

This reuses the same capture/job, applies normal admission limits and returns 409 for content-terminal or non-dead
work. A 429/503 includes `Retry-After`; wait rather than looping.

Fence and mark one active job dead:

```http
POST /api/v1/admin/jobs/{jobId}/mark-dead
```

This may finish an already-started provider request at the network level, but cleared lease ownership prevents that
stale worker from committing over the operator decision.

Inspect the safe outbox summary:

```http
GET /api/v1/admin/outbox/summary
```

There is deliberately no generic outbox purge endpoint.

## Pause and resume

### Pause new asynchronous capture admission

1. Set `BLAIE_CAPTURE_ACCEPT_ASYNC_ENABLED=false` on every API node that can accept Capture writes.
2. Roll/restart those nodes and wait until all old writer binaries have drained.
3. Verify `POST /api/v1/captures/text` and manual retry return 503 `CAPTURE_PROCESSING_UNAVAILABLE` before any
   capture/job/outbox write.
4. Verify read endpoints (`GET /captures`, `GET /captures/{id}`, `GET /inbox`) still work.
5. Leave publisher, worker and recovery roles enabled so existing work drains.

The flag is startup configuration. Changing one process or one local variable is not a cluster-wide pause.

### Pause all provider execution and drain workers

1. Pause admission first unless another healthy worker fleet will continue consuming the queue.
2. Set `BLAIE_CAPTURE_WORKER_ENABLED=false` on every worker node and perform a graceful rolling restart.
3. Allow the configured shutdown window for in-flight tasks. Confirm active leases reach zero or later expire and
   recover.
4. Keep publisher/recovery available so durable state remains repairable.

Never use `BLAIE_AI_CONCURRENCY_ENABLED=false` as a pause switch. It removes the distributed concurrency/cost guard
and permits unbounded provider calls. Per-provider runtime pause is deferred to the provider-health/circuit-breaker
control plane.

### Resume safely

1. Restore Redis/PostgreSQL/provider health.
2. Enable and verify publisher/recovery roles.
3. Enable workers and confirm Redis pending work, queued age and outbox age decrease.
4. Enable acceptance last, initially with conservative worker/provider limits.
5. Monitor completions, errors and oldest queued age through at least one full retry horizon.

## Read-only PostgreSQL diagnostics

Run these with a read-only database account. They intentionally avoid private content and serialized events.

Job counts by durable state:

```sql
SELECT status, COUNT(*)
FROM processing_jobs
GROUP BY status
ORDER BY status;
```

Oldest active control timestamps:

```sql
SELECT
    MIN(available_at) FILTER (WHERE status = 'queued') AS oldest_queued_available_at,
    MIN(available_at) FILTER (WHERE status = 'retry_wait') AS oldest_retry_available_at,
    MIN(lease_expires_at) FILTER (WHERE status = 'processing') AS oldest_lease_expiry
FROM processing_jobs
WHERE status IN ('queued', 'retry_wait', 'processing');
```

Safe dead-job distribution:

```sql
SELECT last_failure_class, last_error_code, COUNT(*)
FROM processing_jobs
WHERE status = 'dead'
GROUP BY last_failure_class, last_error_code
ORDER BY COUNT(*) DESC;
```

Capture queue outbox summary:

```sql
SELECT
    COUNT(*) AS backlog_count,
    MIN(publication_date) AS oldest_publication_at,
    MAX(last_resubmission_date) AS last_resubmission_at,
    MAX(completion_attempts) AS max_completion_attempts
FROM event_publication
WHERE listener_id = 'capture-text-job-redis-publisher'
  AND event_type = 'com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent'
  AND completion_date IS NULL;
```

Do not expand these queries with `original_text` or `serialized_event` for routine incident handling.

## Rollback and handoff

- Prefer pausing acceptance over rolling back a migration while durable work exists.
- If an application rollback is required, verify its binary understands every applied additive schema column and job
  state. Never reverse or edit Flyway history in place.
- Keep PostgreSQL and Redis data intact. A queued PostgreSQL job can be redispatched after recovery.
- Handoff must include UTC timeline, alerts, affected job IDs, safe error/failure classes, config changes, admin
  request IDs, whether admission/workers remain paused and the exact recovery evidence.
