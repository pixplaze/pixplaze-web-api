-- Тестовые заявки (серверы в статусах PENDING / REJECTED / EXPIRED с кодом регистрации): только профиль dev.
-- Идемпотентно: заявка опознаётся по своему коду (voucher_code), повторный прогон ничего не дублирует.
-- Владельцы — профили по имени (notch, e2eowner); если такого профиля нет, сервер остаётся без владельца.
-- Открытые заявки свежее срока жизни (app.servers.bids.ttl, 7d), иначе их сразу закроет проверка как EXPIRED;
-- адреса — в зоне .example, чтобы проверка MOTD их не находила.
WITH data_source (code, name, host, port, status, applicant, age) AS (
    VALUES ('BIDNAT01', 'Sky Islands',       'sky.pixplaze.example',      25565, 'PENDING',  'notch',    interval '2 hours'),
           ('BIDPLG01', 'Pixplaze Survival', 'survival.pixplaze.example', 25565, 'PENDING',  'notch',    interval '1 day'),
           ('BIDPLG02', 'Creative Hub',      'creative.example',          25570, 'PENDING',  'e2eowner', interval '6 days 12 hours'),
           ('BIDNAT02', 'Anarchy 2b',        'anarchy.example',           25565, 'PENDING',  'e2eowner', interval '30 minutes'),
           ('BIDREJ01', 'Spam Craft',        'spam.example',              25565, 'REJECTED', 'e2eowner', interval '3 days'),
           ('BIDREJ02', 'Free Diamonds',     'diamonds.example',          19132, 'REJECTED', 'notch',    interval '9 days'),
           ('BIDEXP01', 'Old Realm',         'old-realm.example',         25565, 'EXPIRED',  'notch',    interval '15 days')
), new_rows AS (
    SELECT ds.* FROM data_source ds
    WHERE NOT EXISTS (
        SELECT 1 FROM minecraft_server_bid b JOIN voucher_code v ON v.id = b.voucher_code_id WHERE v.code = ds.code
    )
), vouchers AS (
    -- код открытой заявки ещё не погашен, закрытой — погашен
    INSERT INTO voucher_code (code, description, type, activations_limit, activations_remaining)
    SELECT n.code, 'Тестовая заявка: ' || n.name, 'INVITE_MINECRAFT_SERVER', 1,
           CASE WHEN n.status = 'PENDING' THEN 1 ELSE 0 END
    FROM new_rows n
    ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description
    RETURNING id, code
), servers AS (
    INSERT INTO minecraft_server (name, owner_profile_id, status, created_at)
    SELECT n.name, (SELECT p.id FROM profile p WHERE p.name = n.applicant), n.status, CURRENT_TIMESTAMP - n.age
    FROM new_rows n
    RETURNING id, name
), hosts AS (
    INSERT INTO minecraft_server_host (minecraft_server_id, type, address, port)
    SELECT s.id, 'HOST', n.host, n.port
    FROM servers s JOIN new_rows n ON n.name = s.name
    ON CONFLICT DO NOTHING
), states AS (
    INSERT INTO minecraft_server_state (minecraft_server_id)
    SELECT id FROM servers
    ON CONFLICT (minecraft_server_id) DO NOTHING
)
INSERT INTO minecraft_server_bid (minecraft_server_id, voucher_code_id, created_at)
SELECT s.id, v.id, CURRENT_TIMESTAMP - n.age
FROM new_rows n
JOIN servers s ON s.name = n.name
JOIN vouchers v ON v.code = n.code;
