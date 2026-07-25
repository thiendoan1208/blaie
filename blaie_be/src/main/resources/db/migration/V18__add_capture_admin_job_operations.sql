CREATE TABLE capture_admin_job_operations (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    capture_id UUID NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT capture_admin_job_operations_actor_check CHECK (
        length(actor_id) BETWEEN 1 AND 128
        AND actor_id ~ '^[A-Za-z0-9._:-]+$'
    ),
    CONSTRAINT capture_admin_job_operations_request_check CHECK (
        length(request_id) BETWEEN 1 AND 128
        AND request_id ~ '^[A-Za-z0-9._:-]+$'
    ),
    CONSTRAINT capture_admin_job_operations_operation_check CHECK (
        operation IN ('requeue', 'mark_dead')
    ),
    CONSTRAINT capture_admin_job_operations_reason_check CHECK (
        (operation = 'mark_dead' AND length(trim(reason)) BETWEEN 1 AND 500)
        OR (operation = 'requeue' AND reason IS NULL)
    )
);

CREATE INDEX idx_capture_admin_job_operations_job_occurred
    ON capture_admin_job_operations (job_id, occurred_at DESC, id DESC);

CREATE INDEX idx_capture_admin_job_operations_occurred
    ON capture_admin_job_operations (occurred_at, id);
