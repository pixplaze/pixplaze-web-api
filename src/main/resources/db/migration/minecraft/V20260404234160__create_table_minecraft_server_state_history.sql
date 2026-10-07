-- Замеры состояния сервера для статистики: только добавление. Пишутся пачкой раз в интервал
-- и внеочередно при смене status. Удаление старых замеров и агрегаты — отдельной задачей.
CREATE TABLE minecraft_server_state_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    minecraft_server_id BIGINT NOT NULL REFERENCES minecraft_server(id) ON DELETE CASCADE,
    sampled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL, -- ONLINE | OFFLINE
    integration_status VARCHAR(16) NOT NULL, -- NATIVE | PLUGIN
    ping BIGINT,
    tps DOUBLE PRECISION,
    uptime BIGINT,
    difficulty VARCHAR(16),
    players_online INT,
    players_max INT
);

CREATE INDEX idx_minecraft_server_state_history_server_sampled
    ON minecraft_server_state_history(minecraft_server_id, sampled_at);
