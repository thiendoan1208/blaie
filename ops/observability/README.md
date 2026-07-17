# Blaie capture-processing observability

Commit 5 standardizes a Prometheus/Micrometer metric contract and ships importable Grafana/Prometheus artifacts.
No paid monitoring service is required: Prometheus and Grafana can be self-hosted, or the same metrics can be sent
to a managed compatible service later.

## Artifacts

- `grafana/capture-processing-dashboard.json` - importable dashboard for queue, job, provider, outbox, Redis and
  recovery health.
- `prometheus/capture-alerts.yml` - recording and alert rules.
- `../../docs/runbooks/capture-processing.md` - triage, admin actions, pause/drain and rollback procedures.

The repository does not start Prometheus or Grafana in the application Compose stack yet. Deployment topology,
retention, storage and notification routing are environment concerns. These artifacts deliberately contain no
credentials, hostnames, user identifiers or private content.

## Secure scrape endpoint

The application management plane defaults to `127.0.0.1:8081`; Prometheus therefore scrapes
`http://127.0.0.1:8081/actuator/prometheus` when it is colocated with the application. Deployments may override the
binding through `BLAIE_MANAGEMENT_ADDRESS` and `BLAIE_MANAGEMENT_PORT`, but must keep it on a private network and
must not publish the management port as a public application port. Liveness and readiness probes are available at
`/actuator/health/liveness` and `/actuator/health/readiness` on the same management plane.

Only the required Actuator endpoints should be exposed:

- health/liveness/readiness may be reachable without application authentication but must not show component details;
- Prometheus must be protected by a dedicated scrape credential or a private management network;
- normal application users must not gain access merely because they are authenticated;
- all other Actuator endpoints, especially `env`, `configprops`, `beans`, `heapdump`, `logfile` and `mappings`, must
  remain unexposed/denied.

A normal 15-minute admin access token is not a durable Prometheus credential. Production should use network policy
and/or a separately rotated machine credential. Never commit that credential to Prometheus configuration in this
repository.

## Metric contract

Micrometer dot names become underscore-separated Prometheus names. Labels are bounded enums/provider ids only.
Never add `userId`, `captureId`, `jobId`, `correlationId`, raw error text or user content as labels.

| Micrometer meter | Prometheus series | Labels / meaning |
| --- | --- | --- |
| `capture.queue.depth` | `capture_queue_depth` | durable active jobs by `state=queued|retry_wait|processing` |
| `capture.oldest.queued.age` | `capture_oldest_queued_age_seconds` | oldest queued job age |
| `capture.active.leases` | `capture_active_leases` | processing jobs whose database lease has not expired |
| `capture.job.duration` | `capture_job_duration_seconds_*` | `outcome=completed|retry_scheduled|dead|stale_discarded` |
| `capture.retry` | `capture_retry_total` | `source=automatic|stale_recovery|manual` |
| `capture.dead` | `capture_dead_total` | `source=worker|stale_recovery|operator`, bounded `failure_class` |
| `capture.stale.recovered` | `capture_stale_recovered_total` | expired leases recovered |
| `capture.queued.redispatched` | `capture_queued_redispatched_total` | DB queued reconciliation |
| `capture.provider.duration` | `capture_provider_duration_seconds_*` | bounded `provider`, `outcome` |
| `capture.provider.errors` | `capture_provider_errors_total` | bounded `provider`, `failure_class` |
| `capture.provider.concurrency.wait` | `capture_provider_concurrency_wait_seconds_*` | `provider`, bounded `outcome` |
| `capture.provider.concurrency.usage` | `capture_provider_concurrency_usage` | global Redis permit usage |
| `capture.provider.concurrency.limit` | `capture_provider_concurrency_limit` | configured provider limit |
| `capture.outbox.backlog` | `capture_outbox_backlog` | incomplete capture publications |
| `capture.outbox.oldest.age` | `capture_outbox_oldest_age_seconds` | oldest incomplete publication age |
| `capture.redis.stream.pending` | `capture_redis_stream_pending` | consumer-group pending count |
| `capture.redis.stream.length` | `capture_redis_stream_length` | capture stream length |
| `capture.observability.source.up` | `capture_observability_source_up` | `source=db|redis`, latest sample success |
| `capture.observability.source.last.success` | `capture_observability_source_last_success_seconds` | epoch seconds of last success |

The job, provider and provider-wait timers publish histograms. The dashboard uses `histogram_quantile` for p95/p99.
Configured service-level buckets should cover:

- job: 1s, 5s, 10s, 30s, 60s;
- provider: 250ms, 1s, 2s, 5s, 8s, 15s;
- provider permit wait: 100ms, 500ms, 1s, 5s, 15s.

## Multi-instance aggregation

Queue, active lease, outbox, Redis Stream and provider-permit gauges observe the same global PostgreSQL/Redis state
from every app instance. First keep only series whose matching `(job, instance)` reports
`capture_observability_source_up{source="db|redis"} == 1`, then use `max`, not `sum`. This prevents a retained
last-good value from an unhealthy replica from overriding current values while also avoiding replica-count
inflation. Use `min by (source)` for source health when any unhealthy sampler must be visible.

Counters and timers describe operations performed by a particular process. Aggregate their rates with `sum`, for
example:

```promql
sum(rate(capture_job_duration_seconds_count{outcome="completed"}[5m]))
```

The dashboard follows these rules.

`blaie:capture_active_jobs` must add all three durable active states from `capture_queue_depth`: `queued`,
`retry_wait` and `processing`. It deliberately does not substitute `capture_active_leases` for processing depth:
an expired processing lease is still active for admission and remains backlog until recovery changes its state.

## Import

1. Configure Prometheus to scrape the secured application management endpoint at no more than a 30-second interval.
2. Load `prometheus/capture-alerts.yml` through the Prometheus rule-file configuration.
3. Import `grafana/capture-processing-dashboard.json` and select the intended Prometheus datasource.
4. Route `severity=critical` and `severity=warning` to the appropriate on-call destinations.
5. Make the repository runbook available at the URL used by the alerting system, or rewrite each `runbook`
   annotation during deployment.

## Thresholds that must follow deployment config

The committed defaults assume:

- global active-job limit: 1,000;
- oldest queued admission limit: five minutes;
- outbox recovery age/interval: roughly ten seconds each;
- Prometheus scrape and observability sample interval: no more than 30 seconds.

`BlaieCaptureActiveQueueHigh` is fixed at 800 (80 percent of the default global limit). Change it whenever
`BLAIE_CAPTURE_MAX_ACTIVE_JOBS_TOTAL` changes. Provider/error/dead/pending thresholds also need tuning after load
tests and real traffic; do not loosen the correctness/admission limits merely to silence an alert.

## Failure semantics

When a DB/Redis observation fails, the exporter retains diagnosis through:

- `capture_observability_source_up{source=...} = 0`;
- a non-advancing `capture_observability_source_last_success_seconds` timestamp.

Consumers must not interpret a stale queue/outbox/Redis gauge as current. The alert rules detect source failure and
staleness before operators act on workload gauges.

## Validation

Before deployment:

- parse the Grafana file as JSON;
- run `promtool check rules ops/observability/prometheus/capture-alerts.yml` with the production Prometheus version;
- scrape a running application and confirm every metric used by the dashboard/rules exists;
- verify an anonymous/normal application user cannot scrape Prometheus and unexposed Actuator endpoints return 404;
- use `promtool test rules` with environment-specific alert fixtures before enabling paging.
