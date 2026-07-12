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

CREATE INDEX IF NOT EXISTS idx_paper_chunks_content_fts
    ON paper_chunks USING gin (to_tsvector('simple', content));

CREATE INDEX IF NOT EXISTS idx_paper_chunks_paper_id
    ON paper_chunks (paper_id);

CREATE INDEX IF NOT EXISTS idx_papers_published_at
    ON papers (published_at);

-- Chat sessions table
CREATE TABLE IF NOT EXISTS chat_sessions (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    scope       VARCHAR(20) NOT NULL,
    paper_id    BIGINT REFERENCES papers(id) ON DELETE CASCADE,
    rolling_summary TEXT,
    state_json TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE chat_sessions
    ADD COLUMN IF NOT EXISTS rolling_summary TEXT;

ALTER TABLE chat_sessions
    ADD COLUMN IF NOT EXISTS state_json TEXT;

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

CREATE TABLE IF NOT EXISTS conversation_memories (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT REFERENCES chat_sessions(id) ON DELETE CASCADE,
    scope       VARCHAR(30) NOT NULL,
    memory_type VARCHAR(50) NOT NULL,
    content     TEXT NOT NULL,
    importance  DOUBLE PRECISION NOT NULL,
    confidence  DOUBLE PRECISION NOT NULL,
    source      VARCHAR(50),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE conversation_memories
    ADD COLUMN IF NOT EXISTS source VARCHAR(50);

ALTER TABLE conversation_memories
    ADD COLUMN IF NOT EXISTS reason VARCHAR(500);

ALTER TABLE conversation_memories
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

ALTER TABLE conversation_memories
    ADD COLUMN IF NOT EXISTS embedding vector(1024);

CREATE INDEX IF NOT EXISTS idx_conversation_memories_embedding
    ON conversation_memories USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_conversation_memories_session_updated
    ON conversation_memories (session_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_conversation_memories_scope_type
    ON conversation_memories (scope, memory_type);

-- Writing versions table
CREATE TABLE IF NOT EXISTS writing_versions (
    id              BIGSERIAL PRIMARY KEY,
    topic           VARCHAR(500),
    outline         TEXT,
    draft           TEXT,
    references_json TEXT,
    version_number  INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS review_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'custom',
    source_filename VARCHAR(255),
    content_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS review_session (
    id BIGSERIAL PRIMARY KEY,
    paper_id BIGINT NOT NULL REFERENCES papers(id) ON DELETE CASCADE,
    template_id BIGINT NOT NULL REFERENCES review_template(id) ON DELETE RESTRICT,
    result_text TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_review_session_paper_id ON review_session (paper_id);
CREATE INDEX IF NOT EXISTS idx_review_session_template_id ON review_session (template_id);

-- Autonomous writing sessions table
CREATE TABLE IF NOT EXISTS autonomous_writing_session (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(500) NOT NULL,
    current_phase VARCHAR(30) NOT NULL DEFAULT 'topic_analysis',
    topic_analysis_json TEXT,
    search_results_json TEXT,
    imported_paper_ids TEXT,
    outline TEXT,
    draft TEXT,
    final_draft TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
