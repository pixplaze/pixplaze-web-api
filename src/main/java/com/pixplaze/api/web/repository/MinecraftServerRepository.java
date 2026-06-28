package com.pixplaze.api.web.repository;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerPortsInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.server.MinecraftServerStatus;
import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.pixplaze.api.web.data.db.Tables.*;

@Repository
@AllArgsConstructor
public class MinecraftServerRepository {

    private final DSLContext dslContext;

    private static final List<MinecraftServerInfo> PIXPLAZE_SERVER_INFO_LIST = List.of(
            new MinecraftServerInfo(
                    "localhost",
                    new MinecraftServerPortsInfo(25565)
            ),
            new MinecraftServerInfo(
                    "185.23.80.106",
                    new MinecraftServerPortsInfo(50010, 50011, 50012)
            ),
            new MinecraftServerInfo(
                    "mc.pixplaze.net",
                    new MinecraftServerPortsInfo(50010, 50011, 50012)
            ),
            new MinecraftServerInfo(
                    "mc.hypixel.net",
                    new MinecraftServerPortsInfo(25565)
            ),
            new MinecraftServerInfo(
                    "mc.epserv.ru",
                    new MinecraftServerPortsInfo(25565)
            )
    );

    public List<MinecraftServerInfo> getPixplazeServerList() {
        return PIXPLAZE_SERVER_INFO_LIST;
    }

    public MinecraftServerInfo getPixplazeServerInfoById(int id) {
        try {
            return PIXPLAZE_SERVER_INFO_LIST.get(id);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public List<MinecraftServerInfo> getMinecraftServerList() {
        return dslContext.select()
                .from(MINECRAFT_SERVER)
                .fetchInto(MinecraftServerInfo.class);
    }

    public Optional<MinecraftServer> findByHost(String host) {
        return dslContext.select()
                .from(MINECRAFT_SERVER)
                .where(MINECRAFT_SERVER.HOST.eq(host))
                .fetchOptionalInto(MinecraftServer.class);
    }

    public Optional<MinecraftServer> findById(Long id) {
        return dslContext.select()
                .from(MINECRAFT_SERVER)
                .where(MINECRAFT_SERVER.ID.eq(id))
                .fetchOptionalInto(MinecraftServer.class);
    }

    /// Создаёт сервер в статусе ACTIVE (момент успешной регистрации) вместе со строкой состояния.
    @Transactional
    public MinecraftServer createActive(MinecraftServer server) {
        final var created = Objects.requireNonNull(
                dslContext.insertInto(MINECRAFT_SERVER)
                        .set(MINECRAFT_SERVER.NAME, server.getName())
                        .set(MINECRAFT_SERVER.HOST, server.getHost())
                        .set(MINECRAFT_SERVER.MOTD, server.getMotd())
                        .set(MINECRAFT_SERVER.IS_LICENSE, server.getIsLicense())
                        .set(MINECRAFT_SERVER.DESCRIPTION, server.getDescription())
                        .set(MINECRAFT_SERVER.CREATED_AT, OffsetDateTime.now())
                        .returning()
                        .fetchOneInto(MinecraftServer.class),
                "Inserted minecraft_server row must be returned!"
        );

        dslContext.insertInto(MINECRAFT_SERVER_STATE)
                .set(MINECRAFT_SERVER_STATE.MINECRAFT_SERVER_ID, created.getId())
                .set(MINECRAFT_SERVER_STATE.STATUS, MinecraftServerStatus.ACTIVE)
                .execute();

        return created;
    }

    public Optional<MinecraftServerStatus> getStatus(Long serverId) {
        return dslContext.select(MINECRAFT_SERVER_STATE.STATUS)
                .from(MINECRAFT_SERVER_STATE)
                .where(MINECRAFT_SERVER_STATE.MINECRAFT_SERVER_ID.eq(serverId))
                .fetchOptional(MINECRAFT_SERVER_STATE.STATUS);
    }

    public void setStatus(Long serverId, MinecraftServerStatus status) {
        dslContext.update(MINECRAFT_SERVER_STATE)
                .set(MINECRAFT_SERVER_STATE.STATUS, status)
                .set(MINECRAFT_SERVER_STATE.UPDATED_AT, OffsetDateTime.now())
                .where(MINECRAFT_SERVER_STATE.MINECRAFT_SERVER_ID.eq(serverId))
                .execute();
    }

    public void updateHost(Long id, String host) {
        dslContext.update(MINECRAFT_SERVER)
                .set(MINECRAFT_SERVER.HOST, host)
                .where(MINECRAFT_SERVER.ID.eq(id))
                .execute();
    }

    public MinecraftServer createIfNotExist(MinecraftServer server) {
        final var existingServer = findByHost(server.getHost()).orElse(null);

        if (existingServer != null) {
            return existingServer;
        }

        final var createdServer = dslContext.insertInto(MINECRAFT_SERVER)
                .set(MINECRAFT_SERVER.HOST, server.getHost())
                .set(MINECRAFT_SERVER.MOTD, server.getMotd())
                .set(MINECRAFT_SERVER.IS_LICENSE, server.getIsLicense())
                .set(MINECRAFT_SERVER.DESCRIPTION, server.getDescription())
                .set(MINECRAFT_SERVER.CREATED_AT, OffsetDateTime.now())
                .onConflict(MINECRAFT_SERVER.HOST)
                .doNothing()
                .returning()
                .fetchOneInto(MinecraftServer.class);

        if (createdServer != null) {
            return createdServer;
        }

        // Проиграли гонку: строку вставил конкурентный поток между SELECT и INSERT
        return findByHost(server.getHost()).orElseThrow(IllegalStateException::new);
    }

    /// Привязывает игрока-оператора к серверу ({@code is_operator = true}). Идемпотентна:
    /// при повторной привязке той же пары обновляет {@code is_operator}.
    public void linkWithOperator(UUID playerUuid, Long serverId) {
        upsertPlayer(serverId, Objects.requireNonNull(playerUuid, "Player 'uuid' must not be null!"), true);
    }

    /// Апсерт членства игрока на сервере: создаёт строку (или обновляет {@code is_operator} существующей),
    /// {@code is_owner} не трогает. Вызывается при device-входе игрока — фиксирует ребро игрок↔сервер,
    /// благодаря которому хост сервера попадает в aud токена профиля.
    public void upsertPlayer(Long serverId, UUID playerUuid, boolean isOperator) {
        dslContext.insertInto(MINECRAFT_SERVER_PLAYER)
                .set(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID, Objects.requireNonNull(playerUuid, "Player 'uuid' must not be null!"))
                .set(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID, Objects.requireNonNull(serverId, "Server 'id' must not be null!"))
                .set(MINECRAFT_SERVER_PLAYER.IS_OPERATOR, isOperator)
                .onConflict(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID, MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID)
                .doUpdate()
                .set(MINECRAFT_SERVER_PLAYER.IS_OPERATOR, isOperator)
                .execute();
    }

    /// Пакетно привязывает операторов к серверу одним INSERT ({@code is_operator = true}); владелец
    /// ({@code ownerUuid}) помечается {@code is_owner = true}. Существующие пары игнорируются.
    public void linkOperators(Long serverId, Collection<UUID> operatorUuids, UUID ownerUuid) {
        if (operatorUuids.isEmpty()) {
            return;
        }
        var insert = dslContext.insertInto(
                MINECRAFT_SERVER_PLAYER,
                MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID,
                MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID,
                MINECRAFT_SERVER_PLAYER.IS_OPERATOR,
                MINECRAFT_SERVER_PLAYER.IS_OWNER
        );
        for (final var uuid : operatorUuids) {
            insert = insert.values(uuid, serverId, true, uuid.equals(ownerUuid));
        }
        insert.onConflict(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID, MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID)
                .doNothing()
                .execute();
    }

    public boolean isPlayerServerOperator(UUID playerUuid, Long serverId) {
        return dslContext.fetchExists(
                MINECRAFT_SERVER_PLAYER,
                MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(playerUuid)
                        .and(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID.eq(serverId))
                        .and(MINECRAFT_SERVER_PLAYER.IS_OPERATOR.eq(true))
        );
    }

    public boolean isPlayerProfileServerOperator(Long profileId, Long serverId) {
        return dslContext.fetchExists(
                dslContext.selectOne()
                        .from(MINECRAFT_SERVER_PLAYER)
                        .join(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                        .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
                        .and(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID.eq(serverId))
                        .and(MINECRAFT_SERVER_PLAYER.IS_OPERATOR.eq(true))
        );
    }
}
