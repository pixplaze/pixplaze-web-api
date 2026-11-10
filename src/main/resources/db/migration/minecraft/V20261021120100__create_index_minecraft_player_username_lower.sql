-- Поиск профилей по началу ника привязанного игрока без учёта регистра (lower(username) LIKE 'abc%'):
-- text_pattern_ops — чтобы LIKE по префиксу шёл по индексу при любой сортировке базы.
CREATE INDEX idx_minecraft_player_username_lower ON minecraft_player (lower(username) text_pattern_ops);
