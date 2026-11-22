package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.db.tables.ProfileFriendTable;
import com.pixplaze.api.web.data.dto.FriendshipStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.Record;
import org.jooq.*;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.pixplaze.api.web.data.db.Tables.*;
import static org.jooq.impl.DSL.*;

/// Друзья профиля: строка {@code profile_friend} — «profile_id добавил friend_profile_id», дружба — обе строки.
@Repository
@RequiredArgsConstructor
public class ProfileFriendRepository {

    private final DSLContext dslContext;

    /// Добавляет строку «id добавил friendProfileId»; повтор — без изменений.
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
    public List<ProfileFriendPlayer> findOutgoing(Long profileId) {
        return dslContext.select(
                MINECRAFT_PLAYER_PROFILE.PROFILE_ID,
                MINECRAFT_PLAYER.UUID,
                MINECRAFT_PLAYER.USERNAME,
                MINECRAFT_PLAYER.IS_ONLINE_HIDDEN,
                MINECRAFT_PLAYER.LAST_SEEN_AT,
                inline("OUTGOING").as("status"),
                MINECRAFT_PLAYER.SKIN_HEAD
        ).from(PROFILE_FRIEND)
                .join(MINECRAFT_PLAYER_PROFILE).on(PROFILE_FRIEND.FRIEND_PROFILE_ID.eq(MINECRAFT_PLAYER_PROFILE.PROFILE_ID))
                .join(MINECRAFT_PLAYER).on(MINECRAFT_PLAYER.UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                .where(PROFILE_FRIEND.PROFILE_ID.eq(profileId))
                .fetch(Records.mapping(ProfileFriendPlayer::fromRecord));
    }

    /// Кто добавил профиль.
    public List<ProfileFriendPlayer> findIncoming(Long profileId) {
        return dslContext.select(
                        MINECRAFT_PLAYER_PROFILE.PROFILE_ID,
                        MINECRAFT_PLAYER.UUID,
                        MINECRAFT_PLAYER.USERNAME,
                        MINECRAFT_PLAYER.IS_ONLINE_HIDDEN,
                        MINECRAFT_PLAYER.LAST_SEEN_AT,
                        inline("INCOMING").as("status"),
                        MINECRAFT_PLAYER.SKIN_HEAD
                ).from(PROFILE_FRIEND)
                .join(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(PROFILE_FRIEND.PROFILE_ID))
                .join(MINECRAFT_PLAYER).on(MINECRAFT_PLAYER.UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                .where(PROFILE_FRIEND.FRIEND_PROFILE_ID.eq(profileId))
                .fetch(Records.mapping(ProfileFriendPlayer::fromRecord));
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

    public List<ProfileFriendPlayer> findFriends(Long profileId) {
        final var direct = PROFILE_FRIEND.as("direct");
        final var reverse = PROFILE_FRIEND.as("reverse");
        final var selectStep = dslContext.select(
                MINECRAFT_PLAYER_PROFILE.PROFILE_ID,
                MINECRAFT_PLAYER.UUID,
                MINECRAFT_PLAYER.USERNAME,
                MINECRAFT_PLAYER.IS_ONLINE_HIDDEN,
                MINECRAFT_PLAYER.LAST_SEEN_AT,
                friendshipStatus(direct, reverse).as("status"),
                MINECRAFT_PLAYER.SKIN_HEAD
        );

        return profileFriendJoins(selectStep, profileId, direct, reverse)
                .where(friendshipStatus(direct, reverse).ne("NONE"))
                .and(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.ne(profileId))
                .fetch(Records.mapping(ProfileFriendPlayer::fromRecord));
    }

    /// Профили вместе с их привязанными игроками: по строке на игрока, у профиля без игроков — одна
    /// строка с пустым игроком.
    public List<ProfileFriendPlayer> findProfilePlayers(Collection<Long> profileIds) {
        return dslContext.select(
                        MINECRAFT_PLAYER_PROFILE.PROFILE_ID,
                        MINECRAFT_PLAYER.UUID,
                        MINECRAFT_PLAYER.USERNAME,
                        MINECRAFT_PLAYER.IS_ONLINE_HIDDEN,
                        MINECRAFT_PLAYER.LAST_SEEN_AT
                )
                .from(MINECRAFT_PLAYER_PROFILE)
                .leftJoin(MINECRAFT_PLAYER).on(MINECRAFT_PLAYER.UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.in(profileIds))
                .orderBy(MINECRAFT_PLAYER_PROFILE.PROFILE_ID, MINECRAFT_PLAYER.USERNAME)
                .fetch(r -> new ProfileFriendPlayer(
                        r.get(MINECRAFT_PLAYER_PROFILE.PROFILE_ID),
                        r.get(MINECRAFT_PLAYER.UUID),
                        r.get(MINECRAFT_PLAYER.USERNAME),
                        r.get(MINECRAFT_PLAYER.IS_ONLINE_HIDDEN),
                        r.get(MINECRAFT_PLAYER.LAST_SEEN_AT),
                        null,
                        null
                ));
    }

    public List<ProfileFriendPlayer> searchFriends(
            Long profileId,
            String query,
            int limit,
            int offset
    ) {
        final var likeQuery = likePrefix(query.toLowerCase());
        final var direct = PROFILE_FRIEND.as("direct");
        final var reverse = PROFILE_FRIEND.as("reverse");

        final var selectStep = dslContext.select(
                MINECRAFT_PLAYER_PROFILE.PROFILE_ID,
                MINECRAFT_PLAYER.UUID,
                MINECRAFT_PLAYER.USERNAME,
                MINECRAFT_PLAYER.IS_ONLINE_HIDDEN,
                MINECRAFT_PLAYER.LAST_SEEN_AT,
                friendshipStatus(direct, reverse).as("status"),
                MINECRAFT_PLAYER.SKIN_HEAD
        );
        return profileFriendJoins(selectStep, profileId, direct, reverse)
                .where(lower(MINECRAFT_PLAYER.USERNAME).like(likeQuery)) // Оптимизация вместо ILIKE
                .and(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.ne(profileId))
                .orderBy(MINECRAFT_PLAYER.USERNAME.asc())
                .limit(limit)
                .offset(offset)
                .fetch(Records.mapping(ProfileFriendPlayer::fromRecord));
    }

    public record ProfileFriendPlayer(
            Long id,
            UUID playerUuid,
            String username,
            Boolean isOnlineHidden,
            OffsetDateTime lastSeenAt,
            FriendshipStatus status,
            String skinHeadBase64
    ) {
        public static ProfileFriendPlayer fromRecord(
                Long id,
                UUID playerUuid,
                String username,
                Boolean isOnlineHidden,
                OffsetDateTime lastSeenAt,
                String status,
                String skinHeadBase64
        ) {
            return new ProfileFriendPlayer(
                    id,
                    playerUuid,
                    username,
                    isOnlineHidden,
                    lastSeenAt,
                    FriendshipStatus.valueOf(status),
                    skinHeadBase64
            );
        }
    }

    private Field<String> friendshipStatus(ProfileFriendTable direct, ProfileFriendTable reverse) {
        return choose()
                .when(direct.FRIEND_PROFILE_ID.isNotNull().and(reverse.PROFILE_ID.isNotNull()), "MUTUAL")
                .when(direct.FRIEND_PROFILE_ID.isNotNull().and(reverse.PROFILE_ID.isNull()), "OUTGOING")
                .when(direct.FRIEND_PROFILE_ID.isNull().and(reverse.PROFILE_ID.isNotNull()), "INCOMING")
                .otherwise("NONE"); // Задает алиас колонки в результирующем выборке
    }

    private <R extends Record> SelectJoinStep<R> profileFriendJoins(
            SelectSelectStep<R> selectStep,
            Long profileId,
            ProfileFriendTable direct,
            ProfileFriendTable reverse
    ) {
        return selectStep.from(MINECRAFT_PLAYER_PROFILE)
                .join(MINECRAFT_PLAYER).on(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER.UUID))
                .leftJoin(direct).on(direct.PROFILE_ID.eq(profileId)).and(direct.FRIEND_PROFILE_ID.eq(MINECRAFT_PLAYER_PROFILE.PROFILE_ID))
                .leftJoin(reverse).on(reverse.PROFILE_ID.eq(MINECRAFT_PLAYER_PROFILE.PROFILE_ID)).and(reverse.FRIEND_PROFILE_ID.eq(profileId));
    }

    /// Шаблон LIKE «начинается с» в нижнем регистре; `%` и `_` (бывает в никах) ищутся буквально.
    static String likePrefix(String query) {
        final var escaped = query.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return escaped + "%";
    }
}
