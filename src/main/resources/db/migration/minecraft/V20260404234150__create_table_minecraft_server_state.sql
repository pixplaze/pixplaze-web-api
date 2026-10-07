-- Последнее известное состояние сервера — только часто меняющееся (MinecraftServerStateInfo); описание
-- сервера — в minecraft_server. Одна строка на сервер, перезаписывается каждым замером.
-- Прогревает снапшот листинга при старте; ряд замеров для статистики — в minecraft_server_state_history.
CREATE TABLE minecraft_server_state (
    minecraft_server_id BIGINT PRIMARY KEY REFERENCES minecraft_server(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE', -- ONLINE | OFFLINE (BANNED выводится из minecraft_server.banned_at)
    integration_status VARCHAR(16) NOT NULL DEFAULT 'NATIVE', -- NATIVE | PLUGIN
    ping BIGINT,
    tps DOUBLE PRECISION,
    uptime BIGINT,
    difficulty VARCHAR(16),
    players_online INT,
    players_max INT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
)
