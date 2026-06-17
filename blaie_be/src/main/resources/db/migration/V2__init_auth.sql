create table if not exists users (
    id uuid not null primary key,
    username varchar(32) null,
    username_normalized varchar(32) null,
    email varchar(255) null,
    email_normalized varchar(255) null,
    display_name varchar(100) not null,
    avatar_url text null,
    status varchar(20) not null default 'active',
    admin boolean not null default false,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint chk_users_status check (status in ('active', 'disabled', 'deleted')),
    constraint chk_users_username_not_blank check (username is null or length(trim(username)) > 0),
    constraint chk_users_username_normalized_not_blank check (username_normalized is null or length(trim(username_normalized)) > 0),
    constraint chk_users_email_not_blank check (email is null or length(trim(email)) > 0),
    constraint chk_users_email_normalized_not_blank check (email_normalized is null or length(trim(email_normalized)) > 0),
    constraint chk_users_display_name_not_blank check (length(trim(display_name)) > 0)
);

create unique index if not exists uq_users_username_normalized
    on users (username_normalized)
    where username_normalized is not null;

create unique index if not exists uq_users_email_normalized
    on users (email_normalized)
    where email_normalized is not null;

create index if not exists idx_users_status on users (status);
create index if not exists idx_users_created_at on users (created_at);

create table if not exists auth_identities (
    id uuid not null primary key,
    user_id uuid not null references users (id) on delete cascade,
    provider varchar(20) not null,
    provider_subject varchar(255) null,
    username_normalized varchar(32) null,
    email_normalized varchar(255) null,
    email_verified boolean not null default false,
    password_hash text null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint chk_auth_identities_provider check (provider in ('local', 'google')),
    constraint chk_auth_identities_provider_subject_not_blank check (provider_subject is null or length(trim(provider_subject)) > 0),
    constraint chk_auth_identities_username_normalized_not_blank check (username_normalized is null or length(trim(username_normalized)) > 0),
    constraint chk_auth_identities_email_normalized_not_blank check (email_normalized is null or length(trim(email_normalized)) > 0),
    constraint chk_auth_identities_password_hash_not_blank check (password_hash is null or length(trim(password_hash)) > 0),
    constraint chk_auth_identities_local_credentials check (
        provider <> 'local'
        or (
            password_hash is not null
            and (username_normalized is not null or email_normalized is not null)
        )
    ),
    constraint chk_auth_identities_google_subject check (
        provider <> 'google'
        or provider_subject is not null
    )
);

create unique index if not exists uq_auth_identities_provider_subject
    on auth_identities (provider, provider_subject)
    where provider_subject is not null;

create unique index if not exists uq_auth_identities_local_username_normalized
    on auth_identities (username_normalized)
    where provider = 'local' and username_normalized is not null;

create unique index if not exists uq_auth_identities_local_email_normalized
    on auth_identities (email_normalized)
    where provider = 'local' and email_normalized is not null;

create index if not exists idx_auth_identities_user_id on auth_identities (user_id);
create index if not exists idx_auth_identities_provider_email on auth_identities (provider, email_normalized);
create index if not exists idx_auth_identities_provider_username on auth_identities (provider, username_normalized);

create table if not exists refresh_tokens (
    id uuid not null primary key,
    user_id uuid not null references users (id) on delete cascade,
    token_hash varchar(255) not null,
    token_family_id uuid not null,
    client_type varchar(20) not null,
    token_transport varchar(20) not null,
    device_id varchar(255) null,
    user_agent text null,
    ip_address inet null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone null,
    revoked_reason varchar(100) null,
    replaced_by_token_id uuid null references refresh_tokens (id) on delete set null,
    created_at timestamp with time zone not null default now(),
    last_used_at timestamp with time zone null,
    constraint uq_refresh_tokens_token_hash unique (token_hash),
    constraint chk_refresh_tokens_client_type check (client_type in ('web', 'mobile')),
    constraint chk_refresh_tokens_token_transport check (token_transport in ('cookie', 'bearer')),
    constraint chk_refresh_tokens_transport_matches_client check (
        (client_type = 'web' and token_transport = 'cookie')
        or (client_type = 'mobile' and token_transport = 'bearer')
    ),
    constraint chk_refresh_tokens_token_hash_not_blank check (length(trim(token_hash)) > 0),
    constraint chk_refresh_tokens_device_id_not_blank check (device_id is null or length(trim(device_id)) > 0),
    constraint chk_refresh_tokens_revoked_reason_not_blank check (revoked_reason is null or length(trim(revoked_reason)) > 0),
    constraint chk_refresh_tokens_expires_after_created check (expires_at > created_at),
    constraint chk_refresh_tokens_revoked_after_created check (revoked_at is null or revoked_at >= created_at),
    constraint chk_refresh_tokens_last_used_after_created check (last_used_at is null or last_used_at >= created_at)
);

create index if not exists idx_refresh_tokens_user_id on refresh_tokens (user_id);
create index if not exists idx_refresh_tokens_token_family_id on refresh_tokens (token_family_id);
create index if not exists idx_refresh_tokens_expires_at on refresh_tokens (expires_at);
create index if not exists idx_refresh_tokens_revoked_at on refresh_tokens (revoked_at);
create index if not exists idx_refresh_tokens_user_active
    on refresh_tokens (user_id, expires_at)
    where revoked_at is null;
