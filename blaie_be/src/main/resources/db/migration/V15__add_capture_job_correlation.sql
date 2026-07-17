ALTER TABLE processing_jobs
    ADD COLUMN origin_request_id VARCHAR(128);

UPDATE processing_jobs
SET origin_request_id = id::text
WHERE origin_request_id IS NULL;

ALTER TABLE processing_jobs
    ALTER COLUMN origin_request_id SET NOT NULL;

ALTER TABLE processing_jobs
    ALTER COLUMN origin_request_id SET DEFAULT gen_random_uuid()::text;

ALTER TABLE processing_jobs
    ADD CONSTRAINT processing_jobs_origin_request_id_check
    CHECK (
        LENGTH(origin_request_id) BETWEEN 1 AND 128
        AND origin_request_id ~ '^[A-Za-z0-9._:-]+$'
    );

CREATE INDEX idx_event_publication_capture_incomplete
    ON event_publication (event_type, listener_id, publication_date)
    WHERE completion_date IS NULL;
