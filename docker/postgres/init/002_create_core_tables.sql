CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    char_count INTEGER NOT NULL,
    token_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT documents_char_count_non_negative CHECK (char_count >= 0),
    CONSTRAINT documents_token_count_non_negative CHECK (token_count >= 0)
);

CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    heading_path TEXT,
    content TEXT NOT NULL,
    char_count INTEGER NOT NULL,
    token_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT document_chunks_chunk_index_non_negative CHECK (chunk_index >= 0),
    CONSTRAINT document_chunks_char_count_non_negative CHECK (char_count >= 0),
    CONSTRAINT document_chunks_token_count_non_negative CHECK (token_count >= 0),
    CONSTRAINT document_chunks_document_id_chunk_index_key UNIQUE (document_id, chunk_index)
);

CREATE TABLE IF NOT EXISTS embedding_models (
    id BIGSERIAL PRIMARY KEY,
    provider TEXT NOT NULL,
    model_name TEXT NOT NULL,
    dimension INTEGER NOT NULL,
    distance_metric TEXT NOT NULL DEFAULT 'cosine',
    base_url TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT embedding_models_dimension_positive CHECK (dimension > 0),
    CONSTRAINT embedding_models_distance_metric_supported CHECK (distance_metric IN ('cosine')),
    CONSTRAINT embedding_models_provider_model_dimension_key UNIQUE (provider, model_name, dimension)
);

DROP TRIGGER IF EXISTS trg_documents_set_updated_at ON documents;

CREATE TRIGGER trg_documents_set_updated_at
BEFORE UPDATE ON documents
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_embedding_models_set_updated_at ON embedding_models;

CREATE TRIGGER trg_embedding_models_set_updated_at
BEFORE UPDATE ON embedding_models
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS chunk_embeddings_1024 (
    id BIGSERIAL PRIMARY KEY,
    chunk_id BIGINT NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    embedding_model_id BIGINT NOT NULL REFERENCES embedding_models(id) ON DELETE RESTRICT,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chunk_embeddings_1024_chunk_model_key UNIQUE (chunk_id, embedding_model_id)
);

CREATE OR REPLACE FUNCTION ensure_chunk_embedding_1024_model_dimension()
RETURNS TRIGGER AS $$
DECLARE
    model_dimension INTEGER;
BEGIN
    SELECT dimension
    INTO model_dimension
    FROM embedding_models
    WHERE id = NEW.embedding_model_id;

    IF model_dimension IS NULL THEN
        RAISE EXCEPTION 'embedding model % does not exist', NEW.embedding_model_id;
    END IF;

    IF model_dimension <> 1024 THEN
        RAISE EXCEPTION 'embedding model % has dimension %, expected 1024',
            NEW.embedding_model_id, model_dimension;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_chunk_embeddings_1024_model_dimension ON chunk_embeddings_1024;

CREATE TRIGGER trg_chunk_embeddings_1024_model_dimension
BEFORE INSERT OR UPDATE OF embedding_model_id ON chunk_embeddings_1024
FOR EACH ROW
EXECUTE FUNCTION ensure_chunk_embedding_1024_model_dimension();

CREATE OR REPLACE FUNCTION prevent_used_embedding_model_dimension_change()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.dimension <> OLD.dimension
       AND EXISTS (
           SELECT 1
           FROM chunk_embeddings_1024
           WHERE embedding_model_id = OLD.id
       ) THEN
        RAISE EXCEPTION 'cannot change dimension for embedding model % after embeddings are stored',
            OLD.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_embedding_models_prevent_dimension_change ON embedding_models;

CREATE TRIGGER trg_embedding_models_prevent_dimension_change
BEFORE UPDATE OF dimension ON embedding_models
FOR EACH ROW
EXECUTE FUNCTION prevent_used_embedding_model_dimension_change();

CREATE INDEX IF NOT EXISTS idx_chunk_embeddings_1024_embedding
    ON chunk_embeddings_1024
    USING hnsw (embedding vector_cosine_ops);
