-- Адреса сервера: игровой (HOST), карта (MAP), API плагина (API) — не больше одного на тип.
-- address хранится нормализованным (нижний регистр), нормализует приложение.
CREATE TABLE minecraft_server_host (
    minecraft_server_id BIGINT NOT NULL REFERENCES minecraft_server(id) ON DELETE CASCADE,
    type VARCHAR(8) NOT NULL, -- HOST | MAP | API
    address VARCHAR(253) NOT NULL,
    port INT NOT NULL CHECK (port BETWEEN 1 AND 65535),
    -- момент последней записи адреса: по нему снапшот листинга не откатывает адреса к прочитанным раньше
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (minecraft_server_id, type)
);

-- Игровой адрес однозначен: один и тот же сервер нельзя завести дважды
CREATE UNIQUE INDEX uq_minecraft_server_host_game
    ON minecraft_server_host(address, port) WHERE type = 'HOST';
