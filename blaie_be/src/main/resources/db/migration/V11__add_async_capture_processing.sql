UPDATE captures
SET processing_status = 'failed',
    failure_code = 'legacy_processing_interrupted',
    updated_at = CURRENT_TIMESTAMP
WHERE processing_status = 'processing';

DELETE FROM capture_items
WHERE processing_status <> 'completed';

ALTER TABLE capture_items DROP CONSTRAINT capture_items_processing_status_check;
ALTER TABLE capture_items DROP CONSTRAINT capture_items_state_check;
ALTER TABLE capture_items
    ADD CONSTRAINT capture_items_processing_status_check
    CHECK (processing_status = 'completed');
ALTER TABLE capture_items
    ADD CONSTRAINT capture_items_state_check
    CHECK (category IS NOT NULL);

ALTER TABLE capture_items ADD COLUMN item_position INTEGER;

WITH ranked_items AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY capture_id ORDER BY created_at, id) - 1 AS item_position
    FROM capture_items
)
UPDATE capture_items item
SET item_position = ranked.item_position
FROM ranked_items ranked
WHERE item.id = ranked.id;

ALTER TABLE capture_items ALTER COLUMN item_position SET NOT NULL;
ALTER TABLE capture_items
    ADD CONSTRAINT capture_items_capture_position_unique UNIQUE (capture_id, item_position);

CREATE TABLE processing_jobs (
    id UUID PRIMARY KEY,
    capture_id UUID NOT NULL REFERENCES captures (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 4,
    retry_generation INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(100),
    lease_expires_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT processing_jobs_capture_type_unique UNIQUE (capture_id, job_type),
    CONSTRAINT processing_jobs_type_check CHECK (job_type = 'text_classification'),
    CONSTRAINT processing_jobs_status_check CHECK (
        status IN ('queued', 'processing', 'retry_wait', 'completed', 'dead')
    ),
    CONSTRAINT processing_jobs_attempt_count_check CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts
    ),
    CONSTRAINT processing_jobs_retry_generation_check CHECK (retry_generation >= 0),
    CONSTRAINT processing_jobs_state_check CHECK (
        (status IN ('queued', 'retry_wait') AND lease_owner IS NULL AND lease_expires_at IS NULL AND completed_at IS NULL)
        OR (status = 'processing' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL AND completed_at IS NULL)
        OR (status = 'completed' AND lease_owner IS NULL AND lease_expires_at IS NULL AND completed_at IS NOT NULL)
        OR (status = 'dead' AND lease_owner IS NULL AND lease_expires_at IS NULL
            AND completed_at IS NOT NULL AND last_error_code IS NOT NULL)
    )
);

CREATE TABLE capture_idempotency_keys (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idempotency_key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    capture_id UUID NOT NULL REFERENCES captures (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, idempotency_key),
    CONSTRAINT capture_idempotency_expiry_check CHECK (expires_at > created_at)
);

CREATE INDEX idx_processing_jobs_ready
    ON processing_jobs (status, available_at, created_at)
    WHERE status IN ('queued', 'retry_wait');
CREATE INDEX idx_processing_jobs_stale
    ON processing_jobs (lease_expires_at)
    WHERE status = 'processing';
CREATE INDEX idx_processing_jobs_user_active
    ON processing_jobs (user_id, status, created_at DESC)
    WHERE status IN ('queued', 'processing', 'retry_wait');
CREATE INDEX idx_captures_user_status_created
    ON captures (user_id, processing_status, created_at DESC, id DESC);
CREATE INDEX idx_capture_idempotency_expiry
    ON capture_idempotency_keys (expires_at);
