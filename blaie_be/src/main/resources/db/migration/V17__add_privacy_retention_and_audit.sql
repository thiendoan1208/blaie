CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id VARCHAR(128) NOT NULL,
    actor_admin BOOLEAN NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(128),
    outcome VARCHAR(20) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    deduplication_bucket TIMESTAMPTZ,
    CONSTRAINT audit_events_actor_id_check CHECK (
        length(actor_id) BETWEEN 1 AND 128 AND actor_id ~ '^[A-Za-z0-9._:-]+$'
    ),
    CONSTRAINT audit_events_action_check CHECK (
        length(action) BETWEEN 1 AND 80 AND action ~ '^[a-z0-9_.]+$'
    ),
    CONSTRAINT audit_events_resource_type_check CHECK (
        length(resource_type) BETWEEN 1 AND 50 AND resource_type ~ '^[a-z0-9_]+$'
    ),
    CONSTRAINT audit_events_resource_id_check CHECK (
        resource_id IS NULL OR (
            length(resource_id) BETWEEN 1 AND 128
            AND resource_id ~ '^[A-Za-z0-9._:-]+$'
        )
    ),
    CONSTRAINT audit_events_outcome_check CHECK (
        outcome IN ('success', 'denied', 'not_found', 'rejected', 'failed')
    ),
    CONSTRAINT audit_events_request_id_check CHECK (
        length(request_id) BETWEEN 1 AND 128
        AND request_id ~ '^[A-Za-z0-9._:-]+$'
    )
);

CREATE INDEX idx_audit_events_occurred
    ON audit_events (occurred_at DESC, id DESC);
CREATE INDEX idx_audit_events_actor_occurred
    ON audit_events (actor_id, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_events_resource_occurred
    ON audit_events (resource_type, resource_id, occurred_at DESC, id DESC)
    WHERE resource_id IS NOT NULL;
CREATE UNIQUE INDEX uq_audit_events_read_bucket
    ON audit_events (
        actor_id,
        action,
        resource_type,
        COALESCE(resource_id, ''),
        deduplication_bucket
    )
    WHERE deduplication_bucket IS NOT NULL;

CREATE INDEX idx_event_publication_completed_cleanup
    ON event_publication (completion_date, id)
    WHERE completion_date IS NOT NULL;
CREATE INDEX idx_processing_jobs_completed_cleanup
    ON processing_jobs (completed_at, id)
    WHERE status = 'completed';
