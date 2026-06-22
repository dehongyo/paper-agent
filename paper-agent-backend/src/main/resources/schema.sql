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
