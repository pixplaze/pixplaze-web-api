CREATE TABLE refresh_token (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- SHA-256 (hex) непрозрачного refresh-токена; сам токен в БД не хранится
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    -- тип субъекта токена; определяет, какая из owner-колонок ниже значима
    subject_type VARCHAR(16) NOT NULL DEFAULT 'PROFILE',
    -- владелец-профиль: обязателен для PROFILE; для связанного игрока/оператора (src=AAD) тоже заполнен, иначе NULL
    profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE,
    -- для серверных (MAD) токенов: к какому серверу привязан refresh; иначе NULL
    minecraft_server_id BIGINT REFERENCES minecraft_server(id) ON DELETE CASCADE,
    -- для игроковых/операторских токенов: к какому игроку привязан refresh; иначе NULL
    minecraft_player_uuid UUID REFERENCES minecraft_player(uuid) ON DELETE CASCADE,
    -- контекст для перевыпуска access-токена (Authority.Source / набор Authority.Role)
    auth_source VARCHAR(8) NOT NULL,
    -- набор ролей как CSV коротких кодов (RU,RMP,RMO,RMS)
    auth_roles VARCHAR(64) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    -- цепочка ротации: на какой токен заменён этот (для reuse-detection)
    replaced_by BIGINT REFERENCES refresh_token(id) ON DELETE SET NULL,
    -- owner-колонка обязана соответствовать subject_type (профиль для связанного игрока опционален)
    CONSTRAINT chk_refresh_token_owner CHECK (
        (subject_type = 'PROFILE'          AND profile_id IS NOT NULL) OR
        (subject_type = 'MINECRAFT_SERVER' AND minecraft_server_id IS NOT NULL) OR
        (subject_type IN ('MINECRAFT_PLAYER', 'MINECRAFT_OPERATOR') AND minecraft_player_uuid IS NOT NULL)
    )
);

CREATE INDEX idx_refresh_token_profile_id ON refresh_token(profile_id);
CREATE INDEX idx_refresh_token_server_id ON refresh_token(minecraft_server_id);
CREATE INDEX idx_refresh_token_player_uuid ON refresh_token(minecraft_player_uuid);
