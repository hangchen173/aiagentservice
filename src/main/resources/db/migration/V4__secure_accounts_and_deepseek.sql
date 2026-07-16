UPDATE sys_users
SET username = 'disabled_' || id,
    password = '{noop}disabled-account',
    deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE username IN ('admin', 'agent', 'visitor');

UPDATE ai_models
SET provider = 'DEEPSEEK',
    model_name = 'deepseek-v4-flash',
    updated_at = CURRENT_TIMESTAMP
WHERE enabled = TRUE;
