-- Тестовые заявки на регистрацию серверов во всех статусах: применяется только в профиле dev.
-- Идемпотентно: заявка опознаётся по своему коду (voucher_code), повторный прогон ничего не дублирует.
-- Подавшие — профили по имени (notch, e2eowner); если такого профиля нет, заявка остаётся без подавшего.
-- Открытые заявки свежее срока жизни (app.servers.bids.ttl, 7d), иначе их сразу закроет проверка как EXPIRED;
-- адреса открытых — в зоне .example, чтобы проверка MOTD их не находила.
WITH data_source (code, name, host, port, owner_username, status, integration, applicant, age) AS (
    VALUES ('BIDNAT01', 'Sky Islands',       'sky.pixplaze.example',      25565, NULL,        'PENDING',  'NATIVE', 'notch',    interval '2 hours'),
           ('BIDPLG01', 'Pixplaze Survival', 'survival.pixplaze.example', 25565, 'Notch',     'PENDING',  'PLUGIN', 'notch',    interval '1 day'),
           ('BIDPLG02', 'Creative Hub',      'creative.example',          25570, 'Steve',     'PENDING',  'PLUGIN', 'e2eowner', interval '6 days 12 hours'),
           ('BIDNAT02', 'Anarchy 2b',        'anarchy.example',           25565, NULL,        'PENDING',  'NATIVE', 'e2eowner', interval '30 minutes'),
           ('BIDAPR01', 'MineDest',          'tmc.mdest.net',             25565, NULL,        'APPROVED', 'NATIVE', 'notch',    interval '20 days'),
           ('BIDREJ01', 'Spam Craft',        'spam.example',              25565, 'Herobrine', 'REJECTED', 'PLUGIN', 'e2eowner', interval '3 days'),
           ('BIDREJ02', 'Free Diamonds',     'diamonds.example',          19132, NULL,        'REJECTED', 'NATIVE', 'notch',    interval '9 days'),
           ('BIDEXP01', 'Old Realm',         'old-realm.example',         25565, 'Alex',      'EXPIRED',  'PLUGIN', 'notch',    interval '15 days')
), vouchers AS (
    -- код открытой заявки ещё не погашен (PLUGIN-заявкой можно пройти регистрацию плагином), закрытой — погашен
    INSERT INTO voucher_code (code, description, type, activations_limit, activations_remaining)
    SELECT ds.code, 'Тестовая заявка: ' || ds.name, 'INVITE_MINECRAFT_SERVER', 1,
           CASE WHEN ds.status = 'PENDING' THEN 1 ELSE 0 END
    FROM data_source ds
    ON CONFLICT (code) DO NOTHING
    RETURNING id, code
), all_vouchers AS (
    SELECT id, code FROM vouchers
    UNION
    SELECT v.id, v.code FROM voucher_code v JOIN data_source ds ON ds.code = v.code
)
INSERT INTO minecraft_server_bid (name, host, port, owner_username, status, integration, voucher_code_id, profile_id, created_at)
SELECT ds.name, ds.host, ds.port, ds.owner_username, ds.status, ds.integration, v.id,
       (SELECT p.id FROM profile p WHERE p.name = ds.applicant),
       CURRENT_TIMESTAMP - ds.age
FROM data_source ds
JOIN all_vouchers v ON v.code = ds.code
WHERE NOT EXISTS (SELECT 1 FROM minecraft_server_bid b WHERE b.voucher_code_id = v.id);
