-- Оценка сервера игроком. Один голос на пару (сервер, игрок) — PK по паре, повторный вызов
-- переголосовывает (UPSERT). Публичный рейтинг сервера = AVG(rating), число голосов = COUNT(*).
-- Хранение per-player (а не агрегатом на сервере) исключает накрутку одним игроком.
CREATE TABLE minecraft_server_rating (
    minecraft_server_id BIGINT REFERENCES minecraft_server(id) ON DELETE CASCADE NOT NULL,
    minecraft_player_uuid UUID REFERENCES minecraft_player(uuid) ON DELETE CASCADE NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (minecraft_server_id, minecraft_player_uuid)
);
