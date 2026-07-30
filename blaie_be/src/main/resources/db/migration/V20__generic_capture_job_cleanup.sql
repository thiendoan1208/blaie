UPDATE event_publication
SET event_type = 'com.blaie.blaie_be.capture.application.event.CaptureJobQueuedEvent',
    listener_id = 'capture-job-redis-publisher'
WHERE event_type = 'com.blaie.blaie_be.capture.application.event.TextCaptureQueuedEvent'
  AND listener_id = 'capture-text-job-redis-publisher';

UPDATE captures
SET failure_code = 'unexpected_analysis_error'
WHERE failure_code = 'unexpected_classification_error';

UPDATE processing_jobs
SET last_error_code = 'unexpected_analysis_error'
WHERE last_error_code = 'unexpected_classification_error';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM captures capture
        WHERE capture.input_type = 'image'
          AND NOT EXISTS (
              SELECT 1
              FROM capture_assets asset
              WHERE asset.capture_id = capture.id
                AND asset.asset_type = 'image'
          )
    ) THEN
        RAISE EXCEPTION 'image capture without an image asset cannot be hardened';
    END IF;
END
$$;

ALTER TABLE captures
    ALTER COLUMN input_type DROP DEFAULT;

CREATE INDEX idx_storage_deletion_jobs_completed_cleanup
    ON storage_deletion_jobs (completed_at, id)
    WHERE status = 'completed';
