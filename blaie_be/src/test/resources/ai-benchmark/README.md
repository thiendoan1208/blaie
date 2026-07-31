# Capture AI benchmark

`capture-gold-v1.json` is a synthetic, versioned gold set for prompt/config evaluation. It contains no production
Capture content and no credentials.

Normal Maven tests validate the dataset and scorer but skip all provider calls. Live tests require explicit opt-in:

```powershell
.\mvnw.cmd "-Dtest=LiveCaptureAiBenchmarkTest" `
  "-Dblaie.live-ai-benchmark=true" `
  "-Dblaie.benchmark.provider=deepseek" test
```

The DeepSeek run compares the checked-in v5 baseline with the v6 fast profile across the text set, then runs the
reasoned profile only for remaining fast-profile failures.

Gemini is deliberately more restrictive:

```powershell
.\mvnw.cmd "-Dtest=LiveCaptureAiBenchmarkTest" `
  "-Dblaie.live-ai-benchmark=true" `
  "-Dblaie.benchmark.provider=gemini" `
  "-Dblaie.benchmark.gemini.confirm-free-tier=true" `
  "-Dblaie.benchmark.gemini.max-requests=12" `
  "-Dblaie.benchmark.gemini.interval-millis=20000" test
```

Before setting `confirm-free-tier`, check the current project usage in Google AI Studio. The harness refuses more
than 12 Gemini calls, refuses an interval below 20 seconds, performs no automatic retry, and stops immediately on
HTTP 429. Those defaults target at most 3 RPM and preserve 8 of the project's 20 free-tier RPD for application use.
It also records daily benchmark reservations in the ignored `.ai-benchmark-gemini-usage` ledger using Pacific
dates, which prevents a second run from exceeding the 12-request benchmark allowance. Google quotas are
project-wide, so this local guard does not replace checking other application or developer usage on the same day.
Use `-Dblaie.benchmark.gemini.candidate-thinking-level=low` or
`-Dblaie.benchmark.gemini.candidate-max-output-tokens=2048` to vary the candidate on a later quota day without
changing production first.

Gemini comparisons use the same `image-v1` prompt for both profiles. They measure inference controls such as
thinking level, media resolution and output budget; there is no alternate production prompt or category routing.

Keys are read from environment variables first and then the local ignored `.env`; keys and raw provider responses
are never printed. Reports contain only aggregate metrics and synthetic case IDs.
