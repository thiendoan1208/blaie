alter table auth_action_tokens
    add column if not exists failed_attempt_count integer not null default 0;

alter table auth_action_tokens
    add constraint chk_auth_action_tokens_failed_attempt_count_non_negative
    check (failed_attempt_count >= 0);
