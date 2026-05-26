CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL DEFAULT '',
    status record_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_status_last_message_at_updated_id
    ON chat_sessions (status, last_message_at DESC, updated_at DESC, id DESC);

DROP TRIGGER IF EXISTS trg_chat_sessions_set_updated_at ON chat_sessions;

CREATE TRIGGER trg_chat_sessions_set_updated_at
BEFORE UPDATE ON chat_sessions
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL,
    error_code TEXT,
    char_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chat_messages_role_supported CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT chat_messages_status_supported CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chat_messages_char_count_non_negative CHECK (char_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created_id
    ON chat_messages (session_id, created_at DESC, id DESC);

DROP TRIGGER IF EXISTS trg_chat_messages_set_updated_at ON chat_messages;

CREATE TRIGGER trg_chat_messages_set_updated_at
BEFORE UPDATE ON chat_messages
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS chat_message_sources (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    note_chunk_id BIGINT NOT NULL REFERENCES note_chunks(id) ON DELETE RESTRICT,
    source_order INTEGER NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chat_message_sources_source_order_positive CHECK (source_order > 0),
    CONSTRAINT chat_message_sources_message_source_order_unique UNIQUE (message_id, source_order)
);
