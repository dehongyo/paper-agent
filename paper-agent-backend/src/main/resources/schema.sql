-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Papers table
CREATE TABLE IF NOT EXISTS papers (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(500) NOT NULL,
    authors     VARCHAR(1000),
    filename    VARCHAR(255) NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    page_count  INTEGER,
    summary     TEXT,
    doi         VARCHAR(255),
    source_url  VARCHAR(1000),
    published_at DATE,
    notes       TEXT,
    tags        TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Paper chunks table (with vector)
CREATE TABLE IF NOT EXISTS paper_chunks (
    id          BIGSERIAL PRIMARY KEY,
    paper_id    BIGINT NOT NULL REFERENCES papers(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1024),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Vector index
CREATE INDEX IF NOT EXISTS idx_paper_chunks_embedding
    ON paper_chunks USING hnsw (embedding vector_cosine_ops);

-- Chat sessions table
CREATE TABLE IF NOT EXISTS chat_sessions (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    scope       VARCHAR(20) NOT NULL,
    paper_id    BIGINT REFERENCES papers(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Chat messages table
CREATE TABLE IF NOT EXISTS chat_messages (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role          VARCHAR(20) NOT NULL,
    content       TEXT NOT NULL,
    evidence_json TEXT,
    message_order INTEGER NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_scope_paper_updated
    ON chat_sessions (scope, paper_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_order
    ON chat_messages (session_id, message_order ASC);
