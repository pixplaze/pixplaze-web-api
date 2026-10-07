CREATE TABLE minecraft_server_favorite (
    profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE NOT NULL,
    minecraft_server_id BIGINT REFERENCES minecraft_server(id) ON DELETE CASCADE NOT NULL,
    favorited_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id, minecraft_server_id)
)
