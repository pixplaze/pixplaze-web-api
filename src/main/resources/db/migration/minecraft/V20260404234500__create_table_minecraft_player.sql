CREATE TABLE minecraft_player (
    uuid UUID PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)