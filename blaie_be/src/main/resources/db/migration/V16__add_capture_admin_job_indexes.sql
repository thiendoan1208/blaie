CREATE INDEX idx_processing_jobs_admin_created
    ON processing_jobs (created_at DESC, id DESC);

CREATE INDEX idx_processing_jobs_admin_status_created
    ON processing_jobs (status, created_at DESC, id DESC);
