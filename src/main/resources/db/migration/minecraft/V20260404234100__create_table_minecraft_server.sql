CREATE TABLE minecraft_server (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    host VARCHAR(128) NOT NULL UNIQUE,
    -- motd / is_license заполняются самим сервером после регистрации, на этапе заявки их ещё нет
    motd VARCHAR(256),
    is_license BOOLEAN,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
)
