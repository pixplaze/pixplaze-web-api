CREATE TABLE profile_minecraft_player (
    profile_id BIGINT REFERENCES profile(id) NOT NULL,
    minecraft_player_uuid UUID REFERENCES minecraft_player(uuid) NOT NULL UNIQUE,
    PRIMARY KEY (profile_id, minecraft_player_uuid)
)