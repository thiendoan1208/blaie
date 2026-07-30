ALTER TABLE captures
    ADD COLUMN input_type VARCHAR(20) NOT NULL DEFAULT 'text';

ALTER TABLE captures
    DROP CONSTRAINT captures_original_text_check;

ALTER TABLE captures
    ALTER COLUMN original_text DROP NOT NULL;

ALTER TABLE captures
    ADD CONSTRAINT captures_input_type_check
        CHECK (input_type IN ('text', 'image')),
    ADD CONSTRAINT captures_input_content_check
        CHECK (
            (input_type = 'text' AND original_text IS NOT NULL AND length(btrim(original_text)) > 0)
            OR
            (input_type = 'image' AND (original_text IS NULL OR length(btrim(original_text)) > 0))
        );

ALTER TABLE processing_jobs
    DROP CONSTRAINT processing_jobs_type_check;

ALTER TABLE processing_jobs
    ADD CONSTRAINT processing_jobs_type_check
        CHECK (job_type IN ('text_classification', 'image_analysis'));

CREATE TABLE capture_assets (
    id UUID PRIMARY KEY,
    capture_id UUID NOT NULL REFERENCES captures (id) ON DELETE CASCADE,
    asset_type VARCHAR(20) NOT NULL,
    asset_position INTEGER NOT NULL,
    storage_provider VARCHAR(30) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT capture_assets_capture_position_unique UNIQUE (capture_id, asset_position),
    CONSTRAINT capture_assets_object_key_unique UNIQUE (object_key),
    CONSTRAINT capture_assets_type_check CHECK (asset_type = 'image'),
    CONSTRAINT capture_assets_position_check CHECK (asset_position >= 0),
    CONSTRAINT capture_assets_content_type_check CHECK (
        content_type IN ('image/jpeg', 'image/png', 'image/webp')
    ),
    CONSTRAINT capture_assets_size_check CHECK (size_bytes > 0),
    CONSTRAINT capture_assets_sha256_check CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT capture_assets_dimensions_check CHECK (width > 0 AND height > 0)
);

CREATE INDEX idx_capture_assets_capture_id
    ON capture_assets (capture_id, asset_position);

CREATE TABLE storage_deletion_jobs (
    id UUID PRIMARY KEY,
    object_key VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 8,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(100),
    lease_expires_at TIMESTAMPTZ,
    last_error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT storage_deletion_jobs_object_key_unique UNIQUE (object_key),
    CONSTRAINT storage_deletion_jobs_status_check CHECK (
        status IN ('pending', 'processing', 'retry_wait', 'completed')
    ),
    CONSTRAINT storage_deletion_jobs_attempts_check CHECK (
        attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts
    ),
    CONSTRAINT storage_deletion_jobs_state_check CHECK (
        (
            status IN ('pending', 'retry_wait')
            AND lease_owner IS NULL
            AND lease_expires_at IS NULL
            AND completed_at IS NULL
        )
        OR (
            status = 'processing'
            AND lease_owner IS NOT NULL
            AND lease_expires_at IS NOT NULL
            AND completed_at IS NULL
        )
        OR (
            status = 'completed'
            AND lease_owner IS NULL
            AND lease_expires_at IS NULL
            AND completed_at IS NOT NULL
        )
    )
);

CREATE INDEX idx_storage_deletion_jobs_ready
    ON storage_deletion_jobs (available_at, created_at, id)
    WHERE status IN ('pending', 'retry_wait');

CREATE INDEX idx_storage_deletion_jobs_stale
    ON storage_deletion_jobs (lease_expires_at, id)
    WHERE status = 'processing';
