-- Поиск профилей по началу имени без учёта регистра (lower(name) LIKE 'abc%'):
-- text_pattern_ops — чтобы LIKE по префиксу шёл по индексу при любой сортировке базы.
CREATE INDEX idx_profile_name_lower ON profile (lower(name) text_pattern_ops);
