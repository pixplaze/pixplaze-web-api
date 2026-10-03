package com.pixplaze.api.web.repository;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.server.MinecraftServerStatus;
import com.pixplaze.api.web.data.server.ServerPingTarget;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import com.pixplaze.api.web.util.NullUtils;
import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

import static com.pixplaze.api.web.data.db.Tables.*;

@Repository
@AllArgsConstructor
public class MinecraftServerRepository {

    private final DSLContext dslContext;

    public List<MinecraftServerInfo> getMinecraftServerList() {
        return dslContext.select()
                .from(MINECRAFT_SERVER)
                .fetchInto(MinecraftServerInfo.class);
    }

    /// Все залистингованные серверы (авторитетная база из БД) — источник правды для листинга.
    public List<MinecraftServer> findAllListed() {
        return dslContext.selectFrom(MINECRAFT_SERVER)
                .fetchInto(MinecraftServer.class);
    }

    /// Адреса для Tier-2 пинга: по строке на сервер (host + Java-порт; при отсутствии порта — 25565).
    /// {@code min(java_port)} + group by гарантирует одну строку на сервер даже при нескольких портах.
    public List<ServerPingTarget> findPingTargets() {
        return dslContext.select(
                        MINECRAFT_SERVER.ID,
                        MINECRAFT_SERVER.HOST,
                        DSL.coalesce(DSL.min(MINECRAFT_SERVER_PORT.JAVA_PORT), DSL.inline(25565)))
                .from(MINECRAFT_SERVER)
                .leftJoin(MINECRAFT_SERVER_PORT).on(MINECRAFT_SERVER_PORT.MINECRAFT_SERVER_ID.eq(MINECRAFT_SERVER.ID))
                .groupBy(MINECRAFT_SERVER.ID, MINECRAFT_SERVER.HOST)
                .fetch(record -> new ServerPingTarget(
                        record.get(MINECRAFT_SERVER.ID),
                        record.get(MINECRAFT_SERVER.HOST),
                        record.get(2, Integer.class)));
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

    /// Создаёт сервер в статусе ONLINE (момент успешной регистрации) вместе со строкой состояния.
    @Transactional
    public MinecraftServer create(MinecraftServer server, MinecraftServerStatus minecraftServerStatus) {
        final var created = Objects.requireNonNull(
                dslContext.insertInto(MINECRAFT_SERVER)
                        .set(MINECRAFT_SERVER.NAME, server.getName())
                        .set(MINECRAFT_SERVER.HOST, server.getHost())
                        .set(MINECRAFT_SERVER.IS_LICENSE, server.getIsLicense())
                        .set(MINECRAFT_SERVER.DESCRIPTION, server.getDescription())
                        .set(MINECRAFT_SERVER.CREATED_AT, OffsetDateTime.now())
                        .returning()
                        .fetchOneInto(MinecraftServer.class),
                "Inserted minecraft_server row must be returned!"
        );

        dslContext.insertInto(MINECRAFT_SERVER_STATE)
                .set(MINECRAFT_SERVER_STATE.MINECRAFT_SERVER_ID, created.getId())
                .set(MINECRAFT_SERVER_STATE.STATUS, minecraftServerStatus)
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
        if (NullUtils.isNullOrEmpty(operatorUuids)) {
            return;
        }

        // 1. Статический шаблон запроса (План компилируется базой 1 раз)
        var query = dslContext.insertInto(MINECRAFT_SERVER_PLAYER)
                .columns(
                        MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID,
                        MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID,
                        MINECRAFT_SERVER_PLAYER.IS_OPERATOR,
                        MINECRAFT_SERVER_PLAYER.IS_OWNER
                )
                .onConflict(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID, MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID)
                .doNothing();

        var batch = dslContext.batch(query);

        for (UUID uuid : operatorUuids) {
            batch.bind(
                    uuid,
                    serverId,
                    uuid.equals(ownerUuid)
            );
        }

        batch.execute();
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

    public void addFavorite(Long serverId, Long profileId) {
        dslContext.insertInto(MINECRAFT_SERVER_FAVORITE)
                .set(MINECRAFT_SERVER_FAVORITE.MINECRAFT_SERVER_ID, serverId)
                .set(MINECRAFT_SERVER_FAVORITE.PROFILE_ID, profileId)
                .onConflict(MINECRAFT_SERVER_FAVORITE.MINECRAFT_SERVER_ID, MINECRAFT_SERVER_FAVORITE.PROFILE_ID)
                .doNothing()
                .execute();
    }

    public void addFavorite(List<Long> serverIds, Long profileId) {
        if (NullUtils.isNullOrEmpty(serverIds)) {
            return;
        }

        var query = dslContext.insertInto(MINECRAFT_SERVER_FAVORITE)
                .columns(MINECRAFT_SERVER_FAVORITE.MINECRAFT_SERVER_ID, MINECRAFT_SERVER_FAVORITE.PROFILE_ID)
                .onDuplicateKeyIgnore();

        var batch = dslContext.batch(query);

        for (Long serverId : serverIds) {
            batch.bind(serverId, profileId);
        }

        batch.execute();
    }


    public void removeFavorite(Long serverId, Long profileId) {
        dslContext.deleteFrom(MINECRAFT_SERVER_FAVORITE)
                .where(MINECRAFT_SERVER_FAVORITE.MINECRAFT_SERVER_ID.eq(serverId).and(MINECRAFT_SERVER_FAVORITE.PROFILE_ID.eq(profileId)))
                .execute();
    }

    public void removeFavorite(Collection<Long> serverIds, Long profileId) {
        if (NullUtils.isNullOrEmpty(serverIds)) {
            return;
        }

        var query = dslContext.deleteFrom(MINECRAFT_SERVER_FAVORITE)
                .where(MINECRAFT_SERVER_FAVORITE.MINECRAFT_SERVER_ID.eq(NullUtils.nullOf(Long.class)))
                .and(MINECRAFT_SERVER_FAVORITE.PROFILE_ID.eq(profileId));

        var batch = dslContext.batch(query);

        for (var serverId : serverIds) {
            batch.bind(serverId, profileId);
        }

        batch.execute();
    }

    public List<MinecraftServer> getFavorite(Long profileId) {
        return dslContext.select()
                .from(MINECRAFT_SERVER_FAVORITE)
                .join(MINECRAFT_SERVER).on(MINECRAFT_SERVER_FAVORITE.MINECRAFT_SERVER_ID.eq(MINECRAFT_SERVER.ID))
                .where(MINECRAFT_SERVER_FAVORITE.PROFILE_ID.eq(profileId))
                .fetchInto(MinecraftServer.class);
    }

    /// Голос игрока за сервер: UPSERT по паре (сервер, игрок) — повторный вызов переголосовывает
    /// (обновляет оценку и {@code updated_at}), не создавая второй строки → один голос на игрока.
    public void upsertRating(Long serverId, Long profileId, int rating) {
        dslContext.insertInto(MINECRAFT_SERVER_RATING)
                .set(MINECRAFT_SERVER_RATING.MINECRAFT_SERVER_ID, serverId)
                .set(MINECRAFT_SERVER_RATING.PROFILE_ID, profileId)
                .set(MINECRAFT_SERVER_RATING.RATING, (short) rating)
                .onConflict(MINECRAFT_SERVER_RATING.MINECRAFT_SERVER_ID, MINECRAFT_SERVER_RATING.PROFILE_ID)
                .doUpdate()
                .set(MINECRAFT_SERVER_RATING.RATING, (short) rating)
                .set(MINECRAFT_SERVER_RATING.UPDATED_AT, OffsetDateTime.now())
                .execute();
    }

    /// Агрегат рейтинга одного сервера (AVG + COUNT). Пустой агрегат, если голосов ещё нет.
    public ServerRatingAggregate ratingAggregate(Long serverId) {
        final var record = dslContext.select(DSL.avg(MINECRAFT_SERVER_RATING.RATING), DSL.count())
                .from(MINECRAFT_SERVER_RATING)
                .where(MINECRAFT_SERVER_RATING.MINECRAFT_SERVER_ID.eq(serverId))
                .fetchOne();
        if (record == null || record.value2() == 0) {
            return ServerRatingAggregate.EMPTY;
        }
        final var avg = record.value1();
        return new ServerRatingAggregate(avg == null ? 0.0 : avg.doubleValue(), record.value2().longValue());
    }

    /// Агрегаты рейтинга по всем серверам одним запросом (для base-sync листинга).
    /// Серверы без голосов в карту не попадают — вызывающий подставляет {@link ServerRatingAggregate#EMPTY}.
    public Map<Long, ServerRatingAggregate> ratingAggregatesByServer() {
        return dslContext.select(
                        MINECRAFT_SERVER_RATING.MINECRAFT_SERVER_ID,
                        DSL.avg(MINECRAFT_SERVER_RATING.RATING),
                        DSL.count())
                .from(MINECRAFT_SERVER_RATING)
                .groupBy(MINECRAFT_SERVER_RATING.MINECRAFT_SERVER_ID)
                .fetchMap(
                        Record3::value1,
                        record -> new ServerRatingAggregate(
                                record.value2() == null ? 0.0 : record.value2().doubleValue(),
                                record.value3().longValue()));
    }
}
