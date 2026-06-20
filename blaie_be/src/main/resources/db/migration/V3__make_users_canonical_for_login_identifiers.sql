drop index if exists uq_auth_identities_local_username_normalized;
drop index if exists uq_auth_identities_local_email_normalized;
drop index if exists idx_auth_identities_provider_username;
drop index if exists idx_auth_identities_provider_email;

alter table auth_identities
    drop constraint if exists chk_auth_identities_username_normalized_not_blank,
    drop constraint if exists chk_auth_identities_email_normalized_not_blank,
    drop constraint if exists chk_auth_identities_local_credentials;

alter table auth_identities
    drop column if exists username_normalized,
    drop column if exists email_normalized;

alter table auth_identities
    add constraint chk_auth_identities_local_credentials check (
        provider <> 'local' or password_hash is not null
    );
