-- Строка = «профиль profile_id добавил в друзья friend_profile_id». Дружба — обе встречные строки;
-- онлайн друг друга видят только взаимные друзья.
CREATE TABLE profile_friend (
    profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE NOT NULL,
    friend_profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id, friend_profile_id),
    CONSTRAINT chk_profile_friend_not_self CHECK (profile_id <> friend_profile_id)
);

-- Встречная строка (кто добавил меня) ищется по второй колонке.
CREATE INDEX idx_profile_friend_friend_profile_id ON profile_friend(friend_profile_id);
