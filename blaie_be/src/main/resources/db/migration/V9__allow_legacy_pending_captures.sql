ALTER TABLE captures DROP CONSTRAINT captures_processing_status_check;
ALTER TABLE captures DROP CONSTRAINT captures_state_check;

ALTER TABLE captures
    ADD CONSTRAINT captures_processing_status_check
    CHECK (processing_status IN ('pending', 'processing', 'completed', 'failed'));

ALTER TABLE captures
    ADD CONSTRAINT captures_state_check
    CHECK (
        (processing_status IN ('pending', 'processing', 'completed') AND failure_code IS NULL)
        OR (processing_status = 'failed' AND failure_code IS NOT NULL)
    );
