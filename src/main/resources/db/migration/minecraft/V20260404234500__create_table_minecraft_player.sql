CREATE TABLE minecraft_player (
    uuid UUID PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    skin_head TEXT,
    -- когда игрок последний раз ушёл из сети (по снимкам heartbeat, точность — минута)
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
)
