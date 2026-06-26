create table if not exists auth_action_tokens (
    id uuid not null primary key,
    user_id uuid not null references users (id) on delete cascade,
    type varchar(40) not null,
    token_hash varchar(255) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone null,
    revoked_at timestamp with time zone null,
    revoked_reason varchar(100) null,
    created_at timestamp with time zone not null default now(),
    constraint uq_auth_action_tokens_token_hash unique (token_hash),
    constraint chk_auth_action_tokens_type check (type in ('email_verification', 'password_reset')),
    constraint chk_auth_action_tokens_token_hash_not_blank check (length(trim(token_hash)) > 0),
    constraint chk_auth_action_tokens_expires_after_created check (expires_at > created_at),
    constraint chk_auth_action_tokens_consumed_after_created check (consumed_at is null or consumed_at >= created_at),
    constraint chk_auth_action_tokens_revoked_after_created check (revoked_at is null or revoked_at >= created_at),
    constraint chk_auth_action_tokens_revoked_reason_not_blank check (revoked_reason is null or length(trim(revoked_reason)) > 0)
);

create index if not exists idx_auth_action_tokens_user_type
    on auth_action_tokens (user_id, type);

create index if not exists idx_auth_action_tokens_expires_at
    on auth_action_tokens (expires_at);

create index if not exists idx_auth_action_tokens_open
    on auth_action_tokens (user_id, type, expires_at)
    where consumed_at is null and revoked_at is null;
