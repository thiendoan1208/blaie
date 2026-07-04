create index if not exists idx_auth_action_tokens_user_type_created_at
    on auth_action_tokens (user_id, type, created_at desc);
