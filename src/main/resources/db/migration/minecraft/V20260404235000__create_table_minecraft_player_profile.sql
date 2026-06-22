CREATE TABLE minecraft_player_profile (
    minecraft_player_uuid UUID REFERENCES minecraft_player(uuid) ON DELETE CASCADE PRIMARY KEY,
    profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE NOT NULL,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
)