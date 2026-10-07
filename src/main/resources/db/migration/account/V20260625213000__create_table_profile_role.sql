-- Роли профиля сверх выводимых автоматически (USER и роли Minecraft-игрока выводит ProfileService).
-- Выдаётся вручную, например ADMIN: INSERT INTO profile_role (profile_id, role_code) VALUES (<id>, 'RADM');
CREATE TABLE profile_role (
    profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE NOT NULL,
    role_code VARCHAR(4) REFERENCES role(code) ON DELETE CASCADE ON UPDATE CASCADE NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id, role_code)
);
