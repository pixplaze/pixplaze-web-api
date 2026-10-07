package com.pixplaze.api.web.repository;

import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerState;
import com.pixplaze.api.web.data.server.ServerHosts;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import com.pixplaze.api.web.util.AddressUtils;
import com.pixplaze.api.web.util.NullUtils;
import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
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

    /// Все залистингованные серверы (авторитетная база из БД) — источник правды для листинга.
    public List<MinecraftServer> findAllListed() {
        return dslContext.selectFrom(MINECRAFT_SERVER)
                .fetchInto(MinecraftServer.class);
    }

    /// Последнее сохранённое состояние всех серверов одним запросом (для base-sync листинга).
    public Map<Long, MinecraftServerState> findAllStates() {
        return dslContext.selectFrom(MINECRAFT_SERVER_STATE)
                .fetchMap(MINECRAFT_SERVER_STATE.MINECRAFT_SERVER_ID, MinecraftServerState.class);
    }

    /// Адреса одного сервера (HOST/MAP/API — сколько объявлено) с моментом последней записи.
    public ServerHosts findHosts(Long serverId) {
        return toServerHosts(dslContext.selectFrom(MINECRAFT_SERVER_HOST)
                .where(MINECRAFT_SERVER_HOST.MINECRAFT_SERVER_ID.eq(serverId))
                .fetch());
    }

    /// Адреса всех серверов одним запросом, сгруппированные по серверу (для base-sync листинга).
    public Map<Long, ServerHosts> findAllHosts() {
        final var result = new HashMap<Long, ServerHosts>();
        dslContext.selectFrom(MINECRAFT_SERVER_HOST)
                .fetchGroups(MINECRAFT_SERVER_HOST.MINECRAFT_SERVER_ID)
                .forEach((serverId, records) -> result.put(serverId, toServerHosts(records)));
        return result;
    }

    private static ServerHosts toServerHosts(List<? extends Record> records) {
        if (records.isEmpty()) {
            return ServerHosts.EMPTY;
        }

        final var updatedAt = records.stream()
                .map(record -> record.get(MINECRAFT_SERVER_HOST.UPDATED_AT))
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ServerHosts(records.stream().map(MinecraftServerRepository::toHostInfo).toList(), updatedAt);
    }

    /// Занят ли игровой адрес (HOST) уже зарегистрированным сервером.
    public boolean isGameHostTaken(String address, Integer port) {
        return dslContext.fetchExists(
                MINECRAFT_SERVER_HOST,
                MINECRAFT_SERVER_HOST.TYPE.eq(MinecraftServerHostInfo.Type.HOST)
                        .and(MINECRAFT_SERVER_HOST.ADDRESS.eq(AddressUtils.normalizeHost(address)))
                        .and(MINECRAFT_SERVER_HOST.PORT.eq(port))
        );
    }

    /// Записывает адреса сервера: по строке на тип, объявленный повторно тип перезаписывается.
    /// Игровой адрес, занятый другим сервером, отклоняет уникальный индекс {@code uq_minecraft_server_host_game}.
    public void upsertHosts(Long serverId, Collection<MinecraftServerHostInfo> hosts) {
        if (NullUtils.isNullOrEmpty(hosts)) {
            return;
        }

        final var queries = hosts.stream()
                .map(host -> dslContext.insertInto(MINECRAFT_SERVER_HOST)
                        .set(MINECRAFT_SERVER_HOST.MINECRAFT_SERVER_ID, serverId)
                        .set(MINECRAFT_SERVER_HOST.TYPE, Objects.requireNonNull(host.type(), "Host 'type' must not be null!"))
                        .set(MINECRAFT_SERVER_HOST.ADDRESS, AddressUtils.normalizeHost(host.address()))
                        .set(MINECRAFT_SERVER_HOST.PORT, host.port())
                        .set(MINECRAFT_SERVER_HOST.UPDATED_AT, DSL.currentOffsetDateTime())
                        .onConflict(MINECRAFT_SERVER_HOST.MINECRAFT_SERVER_ID, MINECRAFT_SERVER_HOST.TYPE)
                        .doUpdate()
                        .set(MINECRAFT_SERVER_HOST.ADDRESS, DSL.excluded(MINECRAFT_SERVER_HOST.ADDRESS))
                        .set(MINECRAFT_SERVER_HOST.PORT, DSL.excluded(MINECRAFT_SERVER_HOST.PORT))
                        .set(MINECRAFT_SERVER_HOST.UPDATED_AT, DSL.excluded(MINECRAFT_SERVER_HOST.UPDATED_AT)))
                .toList();

        dslContext.batch(queries).execute();
    }

    private static MinecraftServerHostInfo toHostInfo(Record record) {
        return new MinecraftServerHostInfo(
                record.get(MINECRAFT_SERVER_HOST.MINECRAFT_SERVER_ID),
                record.get(MINECRAFT_SERVER_HOST.ADDRESS),
                record.get(MINECRAFT_SERVER_HOST.PORT),
                record.get(MINECRAFT_SERVER_HOST.TYPE));
    }

    public Optional<MinecraftServer> findById(Long id) {
        return dslContext.select()
                .from(MINECRAFT_SERVER)
                .where(MINECRAFT_SERVER.ID.eq(id))
                .fetchOptionalInto(MinecraftServer.class);
    }

    /// Создаёт сервер вместе с адресами и строкой текущего состояния (OFFLINE до первого пинга).
    @Transactional
    public MinecraftServer create(
            MinecraftServer server,
            MinecraftServerStateInfo.IntegrationStatus integrationStatus,
            Collection<MinecraftServerHostInfo> hosts
    ) {
        final var created = Objects.requireNonNull(
                dslContext.insertInto(MINECRAFT_SERVER)
                        .set(MINECRAFT_SERVER.NAME, server.getName())
                        .set(MINECRAFT_SERVER.IS_LICENSE, server.getIsLicense())
                        .set(MINECRAFT_SERVER.OWNER_PROFILE_ID, server.getOwnerProfileId())
                        .set(MINECRAFT_SERVER.DESCRIPTION, server.getDescription())
                        .set(MINECRAFT_SERVER.CREATED_AT, OffsetDateTime.now())
                        .returning()
                        .fetchOneInto(MinecraftServer.class),
                "Inserted minecraft_server row must be returned!"
        );

        dslContext.insertInto(MINECRAFT_SERVER_STATE)
                .set(MINECRAFT_SERVER_STATE.MINECRAFT_SERVER_ID, created.getId())
                .set(MINECRAFT_SERVER_STATE.INTEGRATION_STATUS, integrationStatus)
                .execute();

        upsertHosts(created.getId(), hosts);

        return created;
    }

    /// Online-mode сообщает сам сервер (повторная авторизация, heartbeat); {@code null} — не прислал, не трогаем.
    /// Пишет только изменение; возвращает обновлённую строку, если она изменилась.
    public Optional<MinecraftServer> updateLicense(Long serverId, Boolean isLicense) {
        if (isLicense == null) {
            return Optional.empty();
        }

        return dslContext.update(MINECRAFT_SERVER)
                .set(MINECRAFT_SERVER.IS_LICENSE, isLicense)
                .set(MINECRAFT_SERVER.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(MINECRAFT_SERVER.ID.eq(serverId))
                .and(MINECRAFT_SERVER.IS_LICENSE.isDistinctFrom(isLicense))
                .returning()
                .fetchOptionalInto(MinecraftServer.class);
    }

    /// Описание, которое web-api узнаёт пингом (motd, иконка, ядро): пишет только изменение одним запросом —
    /// сравнение делает БД, поэтому без гонок между экземплярами. Возвращает обновлённую строку, если изменилась.
    public Optional<MinecraftServer> updatePingDescription(Long serverId, String motd, String icon, String coreName, String coreVersion) {
        return dslContext.update(MINECRAFT_SERVER)
                .set(MINECRAFT_SERVER.MOTD, motd)
                .set(MINECRAFT_SERVER.ICON, icon)
                .set(MINECRAFT_SERVER.CORE_NAME, coreName)
                .set(MINECRAFT_SERVER.CORE_VERSION, coreVersion)
                .set(MINECRAFT_SERVER.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(MINECRAFT_SERVER.ID.eq(serverId))
                .and(MINECRAFT_SERVER.MOTD.isDistinctFrom(motd)
                        .or(MINECRAFT_SERVER.ICON.isDistinctFrom(icon))
                        .or(MINECRAFT_SERVER.CORE_NAME.isDistinctFrom(coreName))
                        .or(MINECRAFT_SERVER.CORE_VERSION.isDistinctFrom(coreVersion)))
                .returning()
                .fetchOptionalInto(MinecraftServer.class);
    }

    /// Забанен ли сервер. Несуществующий сервер — не забанен: существование проверяет вызывающий.
    public boolean isBanned(Long serverId) {
        return dslContext.fetchExists(
                MINECRAFT_SERVER,
                MINECRAFT_SERVER.ID.eq(serverId).and(MINECRAFT_SERVER.BANNED_AT.isNotNull())
        );
    }

    /// Банит сервер; повторный бан сохраняет момент первого. Возвращает обновлённую строку, если бан новый.
    public Optional<MinecraftServer> ban(Long serverId) {
        return dslContext.update(MINECRAFT_SERVER)
                .set(MINECRAFT_SERVER.BANNED_AT, OffsetDateTime.now())
                .set(MINECRAFT_SERVER.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(MINECRAFT_SERVER.ID.eq(serverId))
                .and(MINECRAFT_SERVER.BANNED_AT.isNull())
                .returning()
                .fetchOptionalInto(MinecraftServer.class);
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

        // 1. Статический шаблон запроса (План компилируется базой 1 раз); значения — заглушки под bind.
        var query = dslContext.insertInto(MINECRAFT_SERVER_PLAYER)
                .columns(
                        MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID,
                        MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID,
                        MINECRAFT_SERVER_PLAYER.IS_OPERATOR,
                        MINECRAFT_SERVER_PLAYER.IS_OWNER
                )
                .values((UUID) null, (Long) null, (Boolean) null, (Boolean) null)
                .onConflict(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID, MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID)
                .doNothing();

        var batch = dslContext.batch(query);

        for (UUID uuid : operatorUuids) {
            batch.bind(
                    uuid,
                    serverId,
                    true,
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
                .values((Long) null, (Long) null)
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
