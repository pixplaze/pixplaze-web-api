package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.dto.ProfilePlayerInfo;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static com.pixplaze.api.web.data.db.Tables.*;

/// Игроки профиля с их серверами — для настроек; отвязка игрока от профиля.
@Repository
@RequiredArgsConstructor
public class ProfilePlayerRepository {

    private final DSLContext dslContext;

    /// Игроки профиля и серверы каждого (с флагами оператора и основного). Игрок без серверов — с пустым списком.
    public List<ProfilePlayerInfo> findWithServers(Long profileId) {
        final var players = new LinkedHashMap<UUID, ProfilePlayerInfo>();
        dslContext.select(MINECRAFT_PLAYER.UUID, MINECRAFT_PLAYER.USERNAME, MINECRAFT_PLAYER.SKIN_HEAD,
                        MINECRAFT_SERVER.ID, MINECRAFT_SERVER.NAME,
                        MINECRAFT_SERVER_PLAYER.IS_OPERATOR, MINECRAFT_SERVER_PLAYER.IS_PRIMARY)
                .from(MINECRAFT_PLAYER_PROFILE)
                .join(MINECRAFT_PLAYER).on(MINECRAFT_PLAYER.UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                .leftJoin(MINECRAFT_SERVER_PLAYER).on(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER.UUID))
                .leftJoin(MINECRAFT_SERVER).on(MINECRAFT_SERVER.ID.eq(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID))
                .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
                .orderBy(MINECRAFT_PLAYER.USERNAME, MINECRAFT_SERVER.NAME)
                .forEach(record -> {
                    final var player = players.computeIfAbsent(record.value1(), uuid ->
                            new ProfilePlayerInfo(uuid, record.value2(), record.value3(), new ArrayList<>()));
                    if (record.value4() != null) {
                        player.servers().add(new ProfilePlayerInfo.Server(record.value4(), record.value5(),
                                Boolean.TRUE.equals(record.value6()), Boolean.TRUE.equals(record.value7())));
                    }
                });
        return players.values().stream()
                .map(player -> new ProfilePlayerInfo(player.uuid(), player.username(), player.skinHeadBase64(), List.copyOf(player.servers())))
                .toList();
    }

    /// Серверы, на которых состоит игрок.
    public List<Long> findServerIds(UUID playerUuid) {
        return dslContext.select(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID)
                .from(MINECRAFT_SERVER_PLAYER)
                .where(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(playerUuid))
                .fetch(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID);
    }

    /// Отвязывает игрока от профиля и снимает с него признак основного на всех серверах. Членство
    /// на серверах остаётся: игрок там играет, просто без профиля. {@code false} — игрок не этого профиля.
    public boolean unlink(Long profileId, UUID playerUuid) {
        final var deleted = dslContext.deleteFrom(MINECRAFT_PLAYER_PROFILE)
                .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
                .and(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID.eq(playerUuid))
                .execute();
        if (deleted == 0) {
            return false;
        }
        dslContext.update(MINECRAFT_SERVER_PLAYER)
                .set(MINECRAFT_SERVER_PLAYER.IS_PRIMARY, false)
                .where(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(playerUuid))
                .execute();
        return true;
    }
}
