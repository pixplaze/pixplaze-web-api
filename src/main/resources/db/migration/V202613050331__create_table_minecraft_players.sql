CREATE TABLE minecraft_players (
    uuid UUID PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    created_at TIMESTAMP
)