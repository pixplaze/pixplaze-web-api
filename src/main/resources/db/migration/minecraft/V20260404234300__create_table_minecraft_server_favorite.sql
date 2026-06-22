CREATE TABLE minecraft_server_favorite (
    profile_id BIGINT REFERENCES profile(id) NOT NULL,
    minecraft_server_id BIGINT REFERENCES minecraft_server(id) NOT NULL,
    favorited_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (profile_id, minecraft_server_id)
)