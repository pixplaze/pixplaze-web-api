CREATE TABLE profile (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    email VARCHAR(64) UNIQUE,
    password VARCHAR(255),
    -- настройка «скрывать мой онлайн»: друзья не видят, что игроки профиля в сети
    is_online_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE
)
