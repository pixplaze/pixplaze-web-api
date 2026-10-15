-- Друзья профиля. Строка (profile_id, friend_profile_id) значит «A добавил B».
-- Друзья — это когда добавили оба, то есть в таблице есть обе строки: A→B и B→A.
-- Состояние                                =>  Условие
-- Друзья                                   =>  A→B и B→A
-- Исходящая заявка A                       =>  A→B без B→A
-- Входящая заявка A                        =>  B→A без A→B
-- Удалить из друзей / отклонить / отозвать =>  удалить свою строку

CREATE TABLE profile_friend (
    profile_id        BIGINT REFERENCES profile(id) ON DELETE CASCADE NOT NULL,
    friend_profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE NOT NULL,
    added_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id, friend_profile_id),
    CHECK (profile_id <> friend_profile_id)
);