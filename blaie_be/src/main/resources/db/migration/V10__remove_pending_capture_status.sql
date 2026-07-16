UPDATE captures
SET processing_status = 'failed',
    failure_code = 'legacy_pending_not_processed',
    updated_at = CURRENT_TIMESTAMP
WHERE processing_status = 'pending';

UPDATE capture_items
SET processing_status = 'failed',
    updated_at = CURRENT_TIMESTAMP
WHERE processing_status = 'pending';

ALTER TABLE captures DROP CONSTRAINT captures_processing_status_check;
ALTER TABLE captures DROP CONSTRAINT captures_state_check;
ALTER TABLE captures
    ADD CONSTRAINT captures_processing_status_check
    CHECK (processing_status IN ('processing', 'completed', 'failed'));
ALTER TABLE captures
    ADD CONSTRAINT captures_state_check
    CHECK (
        (processing_status IN ('processing', 'completed') AND failure_code IS NULL)
        OR (processing_status = 'failed' AND failure_code IS NOT NULL)
    );

ALTER TABLE capture_items DROP CONSTRAINT capture_items_processing_status_check;
ALTER TABLE capture_items DROP CONSTRAINT capture_items_state_check;
ALTER TABLE capture_items
    ADD CONSTRAINT capture_items_processing_status_check
    CHECK (processing_status IN ('processing', 'completed', 'failed'));
ALTER TABLE capture_items
    ADD CONSTRAINT capture_items_state_check
    CHECK (
        (processing_status = 'completed' AND category IS NOT NULL)
        OR (processing_status IN ('processing', 'failed') AND category IS NULL)
    );
