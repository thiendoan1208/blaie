ALTER TABLE processing_jobs
    ADD COLUMN last_failure_class VARCHAR(32);

UPDATE processing_jobs
SET last_failure_class = CASE
    WHEN last_error_code IN (
        'sensitive_credential_detected',
        'content_policy_blocked'
    )
        THEN 'content_terminal'
    WHEN last_error_code IN (
        'ai_not_configured',
        'ai_provider_not_configured',
        'ai_provider_rejected'
    )
        THEN 'provider_terminal'
    WHEN last_error_code IN (
        'ai_provider_unavailable',
        'ai_invalid_response'
    )
        THEN 'provider_retryable'
    ELSE 'system_retryable'
END
WHERE last_error_code IS NOT NULL;

ALTER TABLE processing_jobs
    ADD CONSTRAINT processing_jobs_failure_class_check
    CHECK (
        last_failure_class IS NULL
        OR last_failure_class IN (
            'content_terminal',
            'provider_terminal',
            'provider_retryable',
            'system_retryable'
        )
    );

-- Keep last_failure_class nullable independently from last_error_code while V12
-- binaries can still write the table. A later migration can tighten the paired
-- metadata invariant after the rolling-upgrade compatibility window closes.
