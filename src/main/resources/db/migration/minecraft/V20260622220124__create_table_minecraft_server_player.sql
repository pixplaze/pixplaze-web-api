CREATE TABLE minecraft_server_player (
    minecraft_player_uuid UUID REFERENCES minecraft_player(uuid) ON DELETE CASCADE NOT NULL,
    minecraft_server_id BIGINT REFERENCES minecraft_server(id) ON DELETE CASCADE NOT NULL,
    is_operator BOOLEAN NOT NULL DEFAULT FALSE,
    -- основной игрок профиля на этом сервере: ровно один на пару профиль + сервер, инвариант держит сервис
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (minecraft_player_uuid, minecraft_server_id)
);
