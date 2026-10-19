-- Reference data for the `role_permission` table: which role grants which permission.
-- Codes are constants of com.pixplaze.api.ext.data.auth.Permissions; roles are Authority.Role codes.
-- The table is fully managed here: repeatable migration re-runs on every checksum change and replaces
-- the whole set, so a removed line removes the permission. Runs after R__insert_values_role.
DELETE FROM role_permission;

INSERT INTO role_permission (role_code, permission_code) VALUES
    ('RUSR', 'sys.profile.*'),
    ('RUSR', 'sys.servers.list.read'),
    ('RUSR', 'sys.servers.rating.*'),
    ('RUSR', 'sys.servers.favorites.*'),

    ('RADM', 'sys.servers.bids.approve'),
    ('RADM', 'sys.servers.delete'),

    ('RSYS', 'sys.*'),

    ('RMPL', 'sys.servers.rating.*'),
    ('RMPL', 'sys.servers.favorites.*'),
    ('RMPL', 'mc.chat.read'),
    ('RMPL', 'mc.chat.write'),

    ('RMOP', 'mc.chat.read'),
    ('RMOP', 'mc.chat.write'),
    ('RMOP', 'mc.console.read'),
    ('RMOP', 'mc.console.write'),

    ('RMOW', 'sys.servers.delete');
