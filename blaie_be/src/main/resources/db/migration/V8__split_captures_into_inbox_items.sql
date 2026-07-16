CREATE TABLE captures (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_text TEXT NOT NULL,
    processing_status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(100),
    ai_provider VARCHAR(50),
    ai_model VARCHAR(100),
    prompt_version VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT captures_original_text_check CHECK (length(btrim(original_text)) > 0),
    CONSTRAINT captures_processing_status_check CHECK (
        processing_status IN ('processing', 'completed', 'failed')
    ),
    CONSTRAINT captures_state_check CHECK (
        (processing_status IN ('processing', 'completed') AND failure_code IS NULL)
        OR (processing_status = 'failed' AND failure_code IS NOT NULL)
    )
);

INSERT INTO captures (
    id, user_id, original_text, processing_status, failure_code,
    ai_provider, ai_model, prompt_version, created_at, updated_at
)
SELECT
    id, user_id, original_text, processing_status, failure_code,
    ai_provider, ai_model, prompt_version, created_at, updated_at
FROM capture_items;

ALTER TABLE capture_items DROP CONSTRAINT capture_items_state_check;
ALTER TABLE capture_items ADD COLUMN capture_id UUID;
UPDATE capture_items SET capture_id = id;
ALTER TABLE capture_items ALTER COLUMN capture_id SET NOT NULL;
ALTER TABLE capture_items
    ADD CONSTRAINT capture_items_capture_id_fkey
    FOREIGN KEY (capture_id) REFERENCES captures (id) ON DELETE CASCADE;

ALTER TABLE capture_items DROP COLUMN source_type;
ALTER TABLE capture_items DROP COLUMN failure_code;
ALTER TABLE capture_items DROP COLUMN ai_provider;
ALTER TABLE capture_items DROP COLUMN ai_model;
ALTER TABLE capture_items DROP COLUMN prompt_version;

ALTER TABLE capture_items DROP CONSTRAINT capture_items_processing_status_check;
ALTER TABLE capture_items
    ADD CONSTRAINT capture_items_processing_status_check
    CHECK (processing_status IN ('pending', 'processing', 'completed', 'failed'));
ALTER TABLE capture_items
    ADD CONSTRAINT capture_items_state_check
    CHECK (
        (processing_status = 'completed' AND category IS NOT NULL)
        OR (processing_status = 'failed' AND category IS NULL)
        OR (processing_status IN ('pending', 'processing') AND category IS NULL)
    );

CREATE INDEX idx_captures_user_created_at
    ON captures (user_id, created_at DESC, id DESC);
CREATE INDEX idx_capture_items_capture_id ON capture_items (capture_id);
