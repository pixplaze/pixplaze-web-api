-- Аудитория токена (aud == Authority.targets()) для перевыпуска access-токена при ротации.
-- CSV хостов/зон: у игрока — хост сервера; у сервера — gateway + хост; у профиля — gateway + хосты
-- серверов, где он оператор. Реплеится в новый access-токен при refresh (хост игрока иначе теряется).
ALTER TABLE refresh_token ADD COLUMN auth_targets VARCHAR(512) NOT NULL DEFAULT '';
