CREATE TABLE minecraft_server_bid (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    -- игровой адрес; при регистрации заявка ищется по паре (host, port) и превращается в запись HOST
    host VARCHAR(253) NOT NULL,
    port INT NOT NULL CHECK (port BETWEEN 1 AND 65535),
    -- MC-ник будущего владельца (is_owner); у сервера без плагина список операторов прислать некому
    owner_username VARCHAR(16),
    -- PENDING | APPROVED | REJECTED | EXPIRED (срок жизни открытой заявки — app.servers.bids.ttl от created_at)
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    integration VARCHAR(16) NOT NULL DEFAULT 'NATIVE', -- NATIVE | PLUGIN
    -- код заявки: у PLUGIN — enrollment-ваучер для конфига плагина, у NATIVE — код для MOTD,
    -- по которому принадлежность сервера подтверждается пингом; в обоих случаях — номер заявки
    voucher_code_id BIGINT NOT NULL REFERENCES voucher_code(id) ON DELETE CASCADE,
    profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_minecraft_server_bid_plugin CHECK (integration = 'NATIVE' OR owner_username IS NOT NULL)
);

-- Адрес занят только открытой заявкой: отклонённая или одобренная его не блокирует
CREATE UNIQUE INDEX uq_minecraft_server_bid_pending_host
    ON minecraft_server_bid(host, port) WHERE status = 'PENDING';
