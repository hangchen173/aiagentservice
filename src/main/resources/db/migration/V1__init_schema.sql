CREATE TABLE IF NOT EXISTS sys_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(120) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    online BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS ai_agents (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    scenario VARCHAR(200) NOT NULL,
    prompt TEXT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    trigger_keywords TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS ai_models (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    temperature NUMERIC(3,2) NOT NULL DEFAULT 0.70,
    max_tokens INTEGER NOT NULL DEFAULT 1200,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGSERIAL PRIMARY KEY,
    visitor_id BIGINT NOT NULL,
    assigned_agent_id BIGINT,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_ai_agent_code VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    sender_type VARCHAR(32) NOT NULL,
    sender_id BIGINT,
    content TEXT NOT NULL,
    message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS model_call_logs (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT,
    model_name VARCHAR(100) NOT NULL,
    agent_code VARCHAR(64),
    prompt_preview TEXT,
    response_preview TEXT,
    status VARCHAR(32) NOT NULL,
    latency_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS tickets (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    requester_id BIGINT,
    assignee_id BIGINT,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS handoff_records (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    from_ai_agent_code VARCHAR(64),
    to_agent_id BIGINT,
    reason TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE sys_users ADD CONSTRAINT uk_sys_users_username UNIQUE (username);
ALTER TABLE sys_users ADD CONSTRAINT ck_sys_users_role CHECK (role IN ('ADMIN', 'AGENT', 'VISITOR'));

ALTER TABLE ai_agents ADD CONSTRAINT uk_ai_agents_code UNIQUE (code);
ALTER TABLE ai_agents ADD CONSTRAINT ck_ai_agents_priority CHECK (priority >= 0);

ALTER TABLE ai_models ADD CONSTRAINT uk_ai_models_provider_model_name UNIQUE (provider, model_name);
ALTER TABLE ai_models ADD CONSTRAINT ck_ai_models_temperature CHECK (temperature >= 0 AND temperature <= 2);
ALTER TABLE ai_models ADD CONSTRAINT ck_ai_models_max_tokens CHECK (max_tokens > 0);

ALTER TABLE chat_sessions ADD CONSTRAINT fk_chat_sessions_visitor FOREIGN KEY (visitor_id) REFERENCES sys_users(id);
ALTER TABLE chat_sessions ADD CONSTRAINT fk_chat_sessions_assigned_agent FOREIGN KEY (assigned_agent_id) REFERENCES sys_users(id);
ALTER TABLE chat_sessions ADD CONSTRAINT fk_chat_sessions_current_ai_agent FOREIGN KEY (current_ai_agent_code) REFERENCES ai_agents(code);
ALTER TABLE chat_sessions ADD CONSTRAINT ck_chat_sessions_status CHECK (status IN ('AI_SERVING', 'PENDING_HANDOFF', 'AGENT_SERVING', 'CLOSED'));

ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id);
ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES sys_users(id);
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_sender_type CHECK (sender_type IN ('VISITOR', 'AI', 'AGENT', 'SYSTEM'));
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_message_type CHECK (message_type IN ('TEXT', 'IMAGE', 'FILE', 'SYSTEM'));

ALTER TABLE model_call_logs ADD CONSTRAINT fk_model_call_logs_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id);
ALTER TABLE model_call_logs ADD CONSTRAINT fk_model_call_logs_agent FOREIGN KEY (agent_code) REFERENCES ai_agents(code);
ALTER TABLE model_call_logs ADD CONSTRAINT ck_model_call_logs_status CHECK (status IN ('SUCCESS', 'FAILED'));
ALTER TABLE model_call_logs ADD CONSTRAINT ck_model_call_logs_latency CHECK (latency_ms IS NULL OR latency_ms >= 0);

ALTER TABLE tickets ADD CONSTRAINT fk_tickets_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id);
ALTER TABLE tickets ADD CONSTRAINT fk_tickets_requester FOREIGN KEY (requester_id) REFERENCES sys_users(id);
ALTER TABLE tickets ADD CONSTRAINT fk_tickets_assignee FOREIGN KEY (assignee_id) REFERENCES sys_users(id);
ALTER TABLE tickets ADD CONSTRAINT ck_tickets_status CHECK (status IN ('OPEN', 'PROCESSING', 'CLOSED'));
ALTER TABLE tickets ADD CONSTRAINT ck_tickets_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));

ALTER TABLE handoff_records ADD CONSTRAINT fk_handoff_records_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id);
ALTER TABLE handoff_records ADD CONSTRAINT fk_handoff_records_from_ai_agent FOREIGN KEY (from_ai_agent_code) REFERENCES ai_agents(code);
ALTER TABLE handoff_records ADD CONSTRAINT fk_handoff_records_to_agent FOREIGN KEY (to_agent_id) REFERENCES sys_users(id);
ALTER TABLE handoff_records ADD CONSTRAINT ck_handoff_records_status CHECK (status IN ('PENDING', 'ACCEPTED', 'CLOSED'));

CREATE INDEX IF NOT EXISTS idx_sys_users_role_online ON sys_users(role, online);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_visitor_updated ON chat_sessions(visitor_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_status_updated ON chat_sessions(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created ON chat_messages(session_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_model_call_logs_session_created ON model_call_logs(session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_model_call_logs_status_created ON model_call_logs(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tickets_status_updated ON tickets(status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_tickets_assignee_status ON tickets(assignee_id, status);
CREATE INDEX IF NOT EXISTS idx_handoff_records_session_created ON handoff_records(session_id, created_at DESC);
