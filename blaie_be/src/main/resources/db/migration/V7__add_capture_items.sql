CREATE TABLE capture_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_type VARCHAR(20) NOT NULL,
    original_text TEXT NOT NULL,
    category VARCHAR(30),
    processing_status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(100),
    ai_provider VARCHAR(50),
    ai_model VARCHAR(100),
    prompt_version VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT capture_items_source_type_check CHECK (source_type = 'text'),
    CONSTRAINT capture_items_original_text_check CHECK (length(btrim(original_text)) > 0),
    CONSTRAINT capture_items_category_check CHECK (
        category IS NULL OR category IN ('task', 'calendar_event', 'reminder', 'information')
    ),
    CONSTRAINT capture_items_processing_status_check CHECK (
        processing_status IN ('pending', 'processing', 'completed', 'failed')
    ),
    CONSTRAINT capture_items_state_check CHECK (
        (processing_status = 'completed' AND category IS NOT NULL AND failure_code IS NULL)
        OR (processing_status IN ('pending', 'processing') AND category IS NULL AND failure_code IS NULL)
        OR (processing_status = 'failed' AND category IS NULL AND failure_code IS NOT NULL)
    )
);

CREATE INDEX idx_capture_items_user_created_at
    ON capture_items (user_id, created_at DESC, id DESC);
