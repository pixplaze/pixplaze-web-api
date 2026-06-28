CREATE TABLE minecraft_server_bid (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    -- host UNIQUE: вариант (б) однозначно находит заявку по host на этапе регистрации
    host VARCHAR(128) NOT NULL UNIQUE,
    -- MC-ник игрока, который станет хозяином сервера (is_owner)
    owner_username VARCHAR(16) NOT NULL,
    voucher_code_id BIGINT REFERENCES voucher_code(id) ON DELETE CASCADE,
    profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
)
