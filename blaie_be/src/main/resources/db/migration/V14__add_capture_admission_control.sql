CREATE TABLE capture_admission_mutex (
    id SMALLINT PRIMARY KEY,
    CONSTRAINT capture_admission_mutex_singleton_check CHECK (id = 1)
);

INSERT INTO capture_admission_mutex (id) VALUES (1);

CREATE INDEX idx_processing_jobs_queued_age
    ON processing_jobs (available_at, id)
    WHERE status = 'queued';
