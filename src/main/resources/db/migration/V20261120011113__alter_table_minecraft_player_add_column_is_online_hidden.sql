ALTER TABLE minecraft_player ADD COLUMN is_online_hidden BOOLEAN DEFAULT FALSE;
ALTER TABLE profile DROP COLUMN is_online_hidden;
