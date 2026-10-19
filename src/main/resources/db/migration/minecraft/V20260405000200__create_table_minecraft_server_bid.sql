-- Коды регистрации сервера: связь «сервер → одноразовый ваучер». Заявка — это сам сервер в статусе PENDING
-- (minecraft_server.status), его игровой адрес — запись HOST. Код из ваучера владелец вписывает в MOTD;
-- сервер регистрируется, когда код подтверждён пингом или плагином. Новый код для того же сервера — новая строка,
-- старые остаются историей; срок жизни заявки (app.servers.bids.ttl) считается от created_at последнего кода.
CREATE TABLE minecraft_server_bid (
    minecraft_server_id BIGINT NOT NULL REFERENCES minecraft_server(id) ON DELETE CASCADE,
    voucher_code_id BIGINT NOT NULL UNIQUE REFERENCES voucher_code(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (minecraft_server_id, voucher_code_id)
);
