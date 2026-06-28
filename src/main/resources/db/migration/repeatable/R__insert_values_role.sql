-- Reference data for the `role` table — kept in sync by hand with com.pixplaze.api.ext.data.Authority.Role.
-- `name` stores the raw enum name (no ROLE_ prefix; the prefix is a Spring-Security presentation concern).
-- Repeatable migration: re-runs on every checksum change, hence the idempotent upsert.
-- Runs before R__insert_values_role_permission.sql (Flyway orders repeatables by description, ascending).
INSERT INTO role (code, name, description) VALUES
    ('RUSR', 'USER',               'Base authenticated application user'),
    ('RADM', 'ADMIN',              'Administrator'),
    ('RSYS', 'SYSTEM',             'Internal system / service principal'),
    ('RMCP', 'MINECRAFT_PLAYER',   'Minecraft player linked to a profile'),
    ('RMCO', 'MINECRAFT_OPERATOR', 'Minecraft server operator (elevated)'),
    ('RMCS', 'MINECRAFT_SERVER',   'Registered Minecraft server instance')
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    description = EXCLUDED.description;
