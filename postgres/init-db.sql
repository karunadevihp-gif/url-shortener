CREATE TABLE IF NOT EXISTS url_mapping (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(10) NOT NULL UNIQUE,
    original_url VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    click_count BIGINT DEFAULT 0,
    last_accessed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_short_code ON url_mapping (short_code);
