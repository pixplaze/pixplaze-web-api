CREATE TABLE minecraft_server (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    -- Описание сервера: меняется редко, у каждого поля один источник. online-mode сообщает сам сервер
    -- (регистрация, повторная авторизация, heartbeat — пинг его не знает); motd, ядро и иконку web-api узнаёт пингом.
    is_license BOOLEAN,
    motd TEXT,
    core_name VARCHAR(64),
    core_version VARCHAR(64),
    icon TEXT,
    -- профиль, подавший заявку на сервер; удаление профиля оставляет сервер в листинге без владельца
    owner_profile_id BIGINT REFERENCES profile(id) ON DELETE SET NULL,
    -- бан — решение модерации; хранится отдельно от живого статуса (ONLINE/OFFLINE выводится из пинга)
    banned_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- момент последнего изменения строки: по нему снапшот листинга не откатывает описание к прочитанному раньше
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
)
