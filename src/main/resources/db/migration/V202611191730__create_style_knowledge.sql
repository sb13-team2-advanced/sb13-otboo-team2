CREATE TABLE style_knowledge
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_key     VARCHAR(100) NOT NULL UNIQUE,
    title            TEXT         NOT NULL,
    content          TEXT         NOT NULL,
    base_color       VARCHAR(50)  NOT NULL,
    combination_type VARCHAR(20)  NOT NULL,
    source_type      VARCHAR(30)  NOT NULL,
    source_url       TEXT,
    review_status    VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    document_version INTEGER      NOT NULL DEFAULT 1,
    metadata         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    embedding_model  VARCHAR(100),
    embedding        VECTOR(384),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_style_knowledge_combination_type
        CHECK (combination_type IN ('BASIC', 'TONAL', 'ACCENT')),

    CONSTRAINT ck_style_knowledge_review_status
        CHECK (review_status IN ('DRAFT', 'APPROVED', 'REJECTED')),

    CONSTRAINT ck_style_knowledge_document_version
        CHECK (document_version > 0),

    CONSTRAINT ck_style_knowledge_metadata
        CHECK (jsonb_typeof(metadata) = 'object'),

    CONSTRAINT ck_style_knowledge_embedding
        CHECK (
            (embedding IS NULL AND embedding_model IS NULL)
                OR
            (embedding IS NOT NULL AND embedding_model IS NOT NULL)
            )
);