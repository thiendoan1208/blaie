ALTER TABLE processing_jobs
    ADD COLUMN dispatch_generation INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_dispatched_at TIMESTAMPTZ,
    ADD COLUMN next_dispatch_at TIMESTAMPTZ;

UPDATE processing_jobs
SET dispatch_generation = 1,
    last_dispatched_at = COALESCE(updated_at, created_at),
    next_dispatch_at = CURRENT_TIMESTAMP
WHERE status = 'queued';

ALTER TABLE processing_jobs
    ADD CONSTRAINT processing_jobs_dispatch_generation_check
        CHECK (dispatch_generation >= 0),
    ADD CONSTRAINT processing_jobs_dispatch_state_check
        CHECK (
            -- V11 binaries omit these columns. Keep the legacy all-null shape writable
            -- during a rolling deploy; the queued reconciler upgrades it to generation 1.
            (
                dispatch_generation = 0
                AND last_dispatched_at IS NULL
                AND next_dispatch_at IS NULL
            )
            OR (
                -- V11 may also transition a durable job without clearing/scheduling
                -- next_dispatch_at, so only invariant metadata is constrained here.
                dispatch_generation > 0
                AND last_dispatched_at IS NOT NULL
            )
        );

DROP INDEX idx_processing_jobs_ready;

CREATE INDEX idx_processing_jobs_retry_ready
    ON processing_jobs (available_at, id)
    WHERE status = 'retry_wait';

CREATE INDEX idx_processing_jobs_dispatch_due
    ON processing_jobs (next_dispatch_at NULLS FIRST, id)
    WHERE status = 'queued';
