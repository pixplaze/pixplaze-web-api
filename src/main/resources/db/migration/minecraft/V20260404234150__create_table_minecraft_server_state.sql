CREATE TABLE minecraft_server_state (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- UNIQUE обеспечивает связь один-к-одному с minecraft_server
    minecraft_server_id BIGINT NOT NULL UNIQUE REFERENCES minecraft_server(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
)
