package com.pixplaze.api.web.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.pixplaze.api.web.data.db.Tables.*;

/// Друзья профиля: строка {@code profile_friend} — «profile_id добавил friend_profile_id», дружба — обе строки.
@Repository
@RequiredArgsConstructor
public class ProfileFriendRepository {

    private final DSLContext dslContext;

    /// Добавляет строку «profileId добавил friendProfileId»; повтор — без изменений.
    public void add(Long profileId, Long friendProfileId) {
        dslContext.insertInto(PROFILE_FRIEND)
                .set(PROFILE_FRIEND.PROFILE_ID, profileId)
                .set(PROFILE_FRIEND.FRIEND_PROFILE_ID, friendProfileId)
                .onConflictDoNothing()
                .execute();
    }

    /// Убирает только свою строку; встречная («меня добавили») остаётся.
    public void remove(Long profileId, Long friendProfileId) {
        dslContext.deleteFrom(PROFILE_FRIEND)
                .where(PROFILE_FRIEND.PROFILE_ID.eq(profileId))
                .and(PROFILE_FRIEND.FRIEND_PROFILE_ID.eq(friendProfileId))
                .execute();
    }

    /// Кого добавил профиль.
    public List<ProfileRef> findOutgoing(Long profileId) {
        return dslContext.select(PROFILE.ID, PROFILE.NAME)
                .from(PROFILE_FRIEND)
                .join(PROFILE).on(PROFILE.ID.eq(PROFILE_FRIEND.FRIEND_PROFILE_ID))
                .where(PROFILE_FRIEND.PROFILE_ID.eq(profileId))
                .orderBy(PROFILE.NAME)
                .fetch(record -> new ProfileRef(record.value1(), record.value2()));
    }

    /// Кто добавил профиль.
    public List<ProfileRef> findIncoming(Long profileId) {
        return dslContext.select(PROFILE.ID, PROFILE.NAME)
                .from(PROFILE_FRIEND)
                .join(PROFILE).on(PROFILE.ID.eq(PROFILE_FRIEND.PROFILE_ID))
                .where(PROFILE_FRIEND.FRIEND_PROFILE_ID.eq(profileId))
                .orderBy(PROFILE.NAME)
                .fetch(record -> new ProfileRef(record.value1(), record.value2()));
    }

    /// Взаимные друзья: есть и строка профиля, и встречная.
    public List<Long> findMutualIds(Long profileId) {
        final var incoming = PROFILE_FRIEND.as("incoming");
        return dslContext.select(PROFILE_FRIEND.FRIEND_PROFILE_ID)
                .from(PROFILE_FRIEND)
                .join(incoming).on(incoming.PROFILE_ID.eq(PROFILE_FRIEND.FRIEND_PROFILE_ID)
                        .and(incoming.FRIEND_PROFILE_ID.eq(PROFILE_FRIEND.PROFILE_ID)))
                .where(PROFILE_FRIEND.PROFILE_ID.eq(profileId))
                .fetch(PROFILE_FRIEND.FRIEND_PROFILE_ID);
    }

    /// Профили вместе с их привязанными игроками: по строке на игрока, у профиля без игроков — одна
    /// строка с пустым игроком.
    public List<ProfilePlayerRow> findProfilePlayers(Collection<Long> profileIds) {
        return dslContext.select(PROFILE.ID, PROFILE.NAME, PROFILE.IS_ONLINE_HIDDEN,
                        MINECRAFT_PLAYER.UUID, MINECRAFT_PLAYER.USERNAME, MINECRAFT_PLAYER.LAST_SEEN_AT)
                .from(PROFILE)
                .leftJoin(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(PROFILE.ID))
                .leftJoin(MINECRAFT_PLAYER).on(MINECRAFT_PLAYER.UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                .where(PROFILE.ID.in(profileIds))
                .orderBy(PROFILE.NAME, MINECRAFT_PLAYER.USERNAME)
                .fetch(record -> new ProfilePlayerRow(record.value1(), record.value2(), Boolean.TRUE.equals(record.value3()),
                        record.value4(), record.value5(), record.value6()));
    }

    public record ProfileRef(Long id, String name) {
    }

    public record ProfilePlayerRow(Long profileId, String profileName, boolean isOnlineHidden,
                                   UUID playerUuid, String username, OffsetDateTime lastSeenAt) {
    }
}
