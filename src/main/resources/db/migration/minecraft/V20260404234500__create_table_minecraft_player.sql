CREATE TABLE minecraft_player (
    uuid UUID PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    skin_head TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)