-- Членство игрока на сервере (игрок↔сервер). is_operator — оператор ли он на этом сервере,
-- is_owner — владелец. Заполняется при регистрации сервера (владелец/операторы) и при device-входе
-- игрока (обычное членство). Источник ролей профиля и хостов в aud его токена.
CREATE TABLE minecraft_server_player (
    minecraft_player_uuid UUID REFERENCES minecraft_player(uuid) ON DELETE CASCADE NOT NULL,
    minecraft_server_id BIGINT REFERENCES minecraft_server(id) ON DELETE CASCADE NOT NULL,
    is_operator BOOLEAN NOT NULL DEFAULT FALSE,
    is_owner BOOLEAN NOT NULL DEFAULT FALSE,
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (minecraft_player_uuid, minecraft_server_id)
);
