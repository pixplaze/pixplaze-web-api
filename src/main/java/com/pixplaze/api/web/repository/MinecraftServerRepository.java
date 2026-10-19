package com.pixplaze.api.web.repository;

import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerPlayer;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerState;
import com.pixplaze.api.web.data.server.MinecraftServerRegistrationStatus;
import com.pixplaze.api.web.data.server.ServerHosts;
import com.pixplaze.api.web.data.server.ServerMembership;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import com.pixplaze.api.web.util.AddressUtils;
import com.pixplaze.api.web.util.NullUtils;
import lombok.AllArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.SelectConditionStep;
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

    /// Зарегистрированный сервер: заявки (PENDING) и закрытые (REJECTED, EXPIRED) не видны ни в одном чтении,
    /// кроме заявок и регистрации.
    private static final Condition ACTIVE = MINECRAFT_SERVER.STATUS.eq(MinecraftServerRegistrationStatus.ACTIVE);

    /// Все залистингованные серверы (авторитетная база из БД) — источник правды для листинга.
    public List<MinecraftServer> findAllListed() {
        return dslContext.selectFrom(MINECRAFT_SERVER)
                .where(ACTIVE)
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

    /// Занят ли игровой адрес (HOST) зарегистрированным сервером или открытой заявкой. Адрес закрытой заявки
    /// свободен: его запись снимается, когда адрес понадобится другому ({@link #upsertHosts}).
    public boolean isGameHostTaken(String address, Integer port) {
        return dslContext.fetchExists(dslContext.selectOne()
                .from(MINECRAFT_SERVER_HOST)
                .join(MINECRAFT_SERVER).on(MINECRAFT_SERVER.ID.eq(MINECRAFT_SERVER_HOST.MINECRAFT_SERVER_ID))
                .where(gameHost(address, port))
                .and(MINECRAFT_SERVER.STATUS.in(MinecraftServerRegistrationStatus.ACTIVE, MinecraftServerRegistrationStatus.PENDING)));
    }

    private static Condition gameHost(String address, Integer port) {
        return MINECRAFT_SERVER_HOST.TYPE.eq(MinecraftServerHostInfo.Type.HOST)
                .and(MINECRAFT_SERVER_HOST.ADDRESS.eq(AddressUtils.normalizeHost(address)))
                .and(MINECRAFT_SERVER_HOST.PORT.eq(port));
    }

    /// Записывает адреса сервера: по строке на тип, объявленный повторно тип перезаписывается.
    /// Игровой адрес закрытой заявки (REJECTED, EXPIRED) переходит к этому серверу — до того она хранит его как
    /// историю; занятый зарегистрированным сервером или открытой заявкой отклоняет уникальный индекс
    /// {@code uq_minecraft_server_host_game}.
    public void upsertHosts(Long serverId, Collection<MinecraftServerHostInfo> hosts) {
        if (NullUtils.isNullOrEmpty(hosts)) {
            return;
        }

        hosts.stream()
                .filter(host -> host.type() == MinecraftServerHostInfo.Type.HOST)
                .forEach(host -> dslContext.deleteFrom(MINECRAFT_SERVER_HOST)
                        .where(gameHost(host.address(), host.port()))
                        .and(MINECRAFT_SERVER_HOST.MINECRAFT_SERVER_ID.in(DSL.select(MINECRAFT_SERVER.ID)
                                .from(MINECRAFT_SERVER)
                                .where(MINECRAFT_SERVER.STATUS.in(MinecraftServerRegistrationStatus.REJECTED, MinecraftServerRegistrationStatus.EXPIRED))))
                        .execute());

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

    /// Сколько из {@code ids} — зарегистрированные серверы (без учёта повторов).
    public int countExisting(Collection<Long> ids) {
        return dslContext.fetchCount(MINECRAFT_SERVER, MINECRAFT_SERVER.ID.in(ids).and(ACTIVE));
    }

    /// Зарегистрированный сервер.
    public Optional<MinecraftServer> findById(Long id) {
        return dslContext.selectFrom(MINECRAFT_SERVER)
                .where(MINECRAFT_SERVER.ID.eq(id))
                .and(ACTIVE)
                .fetchOptionalInto(MinecraftServer.class);
    }

    /// Сервер в любом статусе — для заявок и регистрации.
    public Optional<MinecraftServer> findAnyById(Long id) {
        return dslContext.selectFrom(MINECRAFT_SERVER)
                .where(MINECRAFT_SERVER.ID.eq(id))
                .fetchOptionalInto(MinecraftServer.class);
    }

    /// Заявка: сервер в статусе PENDING с игровым адресом. Занятый адрес отклоняет уникальный индекс.
    @Transactional
    public MinecraftServer createPending(String name, Long ownerProfileId, MinecraftServerHostInfo gameHost) {
        final var created = Objects.requireNonNull(
                dslContext.insertInto(MINECRAFT_SERVER)
                        .set(MINECRAFT_SERVER.NAME, name)
                        .set(MINECRAFT_SERVER.OWNER_PROFILE_ID, ownerProfileId)
                        .set(MINECRAFT_SERVER.STATUS, MinecraftServerRegistrationStatus.PENDING)
                        .set(MINECRAFT_SERVER.CREATED_AT, OffsetDateTime.now())
                        .returning()
                        .fetchOneInto(MinecraftServer.class),
                "Inserted minecraft_server row must be returned!"
        );

        upsertHosts(created.getId(), List.of(gameHost));
        return created;
    }

    /// Регистрирует заявку: PENDING → ACTIVE и строка текущего состояния (OFFLINE до первого пинга).
    /// Пусто — сервер не в статусе PENDING (гонка проверки MOTD, плагина и админа).
    @Transactional
    public Optional<MinecraftServer> activate(Long serverId, MinecraftServerStateInfo.IntegrationStatus integrationStatus) {
        final var activated = setStatus(serverId, MinecraftServerRegistrationStatus.ACTIVE);
        activated.ifPresent(server -> dslContext.insertInto(MINECRAFT_SERVER_STATE)
                .set(MINECRAFT_SERVER_STATE.MINECRAFT_SERVER_ID, server.getId())
                .set(MINECRAFT_SERVER_STATE.INTEGRATION_STATUS, integrationStatus)
                .onConflictDoNothing()
                .execute());
        return activated;
    }

    /// Переводит заявку (PENDING) в другой статус. Пусто — сервер уже не заявка.
    public Optional<MinecraftServer> setStatus(Long serverId, MinecraftServerRegistrationStatus status) {
        return dslContext.update(MINECRAFT_SERVER)
                .set(MINECRAFT_SERVER.STATUS, status)
                .set(MINECRAFT_SERVER.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(MINECRAFT_SERVER.ID.eq(serverId))
                .and(MINECRAFT_SERVER.STATUS.eq(MinecraftServerRegistrationStatus.PENDING))
                .returning()
                .fetchOptionalInto(MinecraftServer.class);
    }

    /// Online-mode сообщает сам сервер (повторная авторизация, heartbeat); {@code null} — не прислал, не трогаем.
    /// Слияние — {@link PartialUpdate}; возвращает обновлённую строку, если она изменилась.
    public Optional<MinecraftServer> updateLicense(Long serverId, Boolean isLicense) {
        return PartialUpdate.of(dslContext, MINECRAFT_SERVER)
                .set(MINECRAFT_SERVER.IS_LICENSE, isLicense)
                .touch(MINECRAFT_SERVER.UPDATED_AT)
                .execute(MINECRAFT_SERVER.ID.eq(serverId), MinecraftServer.class);
    }

    /// Описание, которое web-api узнаёт пингом (motd, иконка, ядро). Чего пинг не вернул ({@code null}),
    /// то остаётся как было — слияние {@link PartialUpdate}. Возвращает обновлённую строку, если изменилась.
    public Optional<MinecraftServer> updatePingDescription(Long serverId, String motd, String icon, String coreName, String coreVersion) {
        return PartialUpdate.of(dslContext, MINECRAFT_SERVER)
                .set(MINECRAFT_SERVER.MOTD, motd)
                .set(MINECRAFT_SERVER.ICON, icon)
                .set(MINECRAFT_SERVER.CORE_NAME, coreName)
                .set(MINECRAFT_SERVER.CORE_VERSION, coreVersion)
                .touch(MINECRAFT_SERVER.UPDATED_AT)
                .execute(MINECRAFT_SERVER.ID.eq(serverId), MinecraftServer.class);
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

    /// Апсерт членства игрока на сервере: создаёт строку (или обновляет {@code is_operator} существующей).
    /// Вызывается при device-входе игрока — фиксирует ребро игрок↔сервер,
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

    /// Пакетно привязывает операторов к серверу одним INSERT ({@code is_operator = true}). Существующие
    /// пары игнорируются. Владелец здесь не помечается: его задаёт {@code minecraft_server.owner_profile_id}.
    public void linkOperators(Long serverId, Collection<UUID> operatorUuids) {
        if (NullUtils.isNullOrEmpty(operatorUuids)) {
            return;
        }

        // 1. Статический шаблон запроса (План компилируется базой 1 раз); значения — заглушки под bind.
        var query = dslContext.insertInto(MINECRAFT_SERVER_PLAYER)
                .columns(
                        MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID,
                        MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID,
                        MINECRAFT_SERVER_PLAYER.IS_OPERATOR
                )
                .values((UUID) null, (Long) null, (Boolean) null)
                .onConflict(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID, MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID)
                .doNothing();

        var batch = dslContext.batch(query);

        for (UUID uuid : operatorUuids) {
            batch.bind(
                    uuid,
                    serverId,
                    true
            );
        }

        batch.execute();
    }


    /// Связь профиля с сервером одним запросом: участник (через своих игроков), оператор, владелец.
    public ServerMembership findMembership(Long profileId, Long serverId) {
        final var isMember = DSL.field(DSL.exists(profilePlayersOnServer(profileId, serverId)));
        final var isOperator = DSL.field(DSL.exists(profilePlayersOnServer(profileId, serverId)
                .and(MINECRAFT_SERVER_PLAYER.IS_OPERATOR.isTrue())));
        final var isOwner = DSL.field(DSL.exists(dslContext.selectOne()
                .from(MINECRAFT_SERVER)
                .where(MINECRAFT_SERVER.ID.eq(serverId))
                .and(MINECRAFT_SERVER.OWNER_PROFILE_ID.eq(profileId))
                .and(ACTIVE)));

        return dslContext.select(isMember, isOperator, isOwner)
                .fetchSingle(record -> new ServerMembership(record.value1(), record.value2(), record.value3()));
    }

    /// Флаг оператора участника сервера из снимка heartbeat. Пишется только изменение; игрок, который
    /// на сервере не участник (строки нет), не затрагивается.
    public void updatePlayerOperator(Long serverId, UUID playerUuid, Boolean isOperator) {
        PartialUpdate.of(dslContext, MINECRAFT_SERVER_PLAYER)
                .set(MINECRAFT_SERVER_PLAYER.IS_OPERATOR, isOperator)
                .execute(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID.eq(serverId)
                        .and(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(playerUuid)), MinecraftServerPlayer.class);
    }

    /// Основной игрок профиля на сервере ({@code is_primary}).
    public Optional<UUID> findPrimaryPlayer(Long profileId, Long serverId) {
        return dslContext.select(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID)
                .from(MINECRAFT_SERVER_PLAYER)
                .join(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
                .and(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID.eq(serverId))
                .and(MINECRAFT_SERVER_PLAYER.IS_PRIMARY.isTrue())
                .fetchOptional(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID);
    }

    /// Держит инвариант «ровно один основной игрок на пару профиль + сервер»: если у профиля на сервере
    /// есть игроки, но нет основного, основным становится самый ранний по {@code linked_at}. Вызывается
    /// после любой привязки (и отвязки) игрока. Частичный уникальный индекс не построить — профиля
    /// в таблице нет, — поэтому параллельные вызовы для профиля сериализуются блокировкой его строки.
    @Transactional
    public void ensurePrimaryPlayer(Long profileId, Long serverId) {
        lockProfile(profileId);
        final var earliest = dslContext.select(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID)
                .from(MINECRAFT_SERVER_PLAYER)
                .join(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
                .and(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID.eq(serverId))
                .orderBy(MINECRAFT_SERVER_PLAYER.LINKED_AT.asc(), MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.asc())
                .limit(1);

        dslContext.update(MINECRAFT_SERVER_PLAYER)
                .set(MINECRAFT_SERVER_PLAYER.IS_PRIMARY, true)
                .where(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID.eq(serverId))
                .and(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(earliest))
                .and(DSL.notExists(profilePlayersOnServer(profileId, serverId).and(MINECRAFT_SERVER_PLAYER.IS_PRIMARY.isTrue())))
                .execute();
    }

    /// Делает {@code playerUuid} основным игроком профиля на сервере, остальные его игроки там перестают
    /// быть основными. {@code false} — игрок не принадлежит профилю или не состоит на сервере.
    @Transactional
    public boolean setPrimaryPlayer(Long profileId, Long serverId, UUID playerUuid) {
        lockProfile(profileId);
        if (!dslContext.fetchExists(profilePlayersOnServer(profileId, serverId).and(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(playerUuid)))) {
            return false;
        }

        dslContext.update(MINECRAFT_SERVER_PLAYER)
                .set(MINECRAFT_SERVER_PLAYER.IS_PRIMARY, DSL.field(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(playerUuid)))
                .where(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID.eq(serverId))
                .and(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.in(dslContext.select(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID)
                        .from(MINECRAFT_PLAYER_PROFILE)
                        .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))))
                .execute();
        return true;
    }

    /// Блокирует строку профиля до конца транзакции: сериализует изменения его основных игроков.
    private void lockProfile(Long profileId) {
        dslContext.select(PROFILE.ID)
                .from(PROFILE)
                .where(PROFILE.ID.eq(profileId))
                .forUpdate()
                .fetch();
    }

    /// Связи профиля со всеми незабаненными серверами, где у него есть роль: сервер → связь. Два запроса:
    /// серверы его игроков (с флагом оператора) и серверы, которыми он владеет.
    public Map<Long, ServerMembership> findMemberships(Long profileId) {
        final var isOperator = DSL.boolOr(MINECRAFT_SERVER_PLAYER.IS_OPERATOR);
        final var memberships = new LinkedHashMap<Long, ServerMembership>();
        dslContext.select(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID, isOperator)
                .from(MINECRAFT_SERVER_PLAYER)
                .join(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                .join(MINECRAFT_SERVER).on(MINECRAFT_SERVER.ID.eq(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID))
                .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
                .and(MINECRAFT_SERVER.BANNED_AT.isNull())
                .groupBy(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID)
                .orderBy(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID)
                .forEach(record -> memberships.put(record.value1(), new ServerMembership(true, Boolean.TRUE.equals(record.value2()), false)));

        dslContext.select(MINECRAFT_SERVER.ID)
                .from(MINECRAFT_SERVER)
                .where(MINECRAFT_SERVER.OWNER_PROFILE_ID.eq(profileId))
                .and(MINECRAFT_SERVER.BANNED_AT.isNull())
                .and(ACTIVE)
                .orderBy(MINECRAFT_SERVER.ID)
                .forEach(record -> memberships.merge(record.value1(), new ServerMembership(false, false, true),
                        (membership, owner) -> new ServerMembership(membership.isMember(), membership.isOperator(), true)));
        return memberships;
    }

    /// Игроки профиля на сервере. Каждый вызов — новый запрос: шаги построения jOOQ изменяемые.
    private SelectConditionStep<Record1<Integer>> profilePlayersOnServer(Long profileId, Long serverId) {
        return dslContext.selectOne()
                .from(MINECRAFT_SERVER_PLAYER)
                .join(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
                .and(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID.eq(serverId));
    }

    /// Состоит ли игрок на сервере (есть строка {@code minecraft_server_player}).
    public boolean isPlayerServerMember(UUID playerUuid, Long serverId) {
        return dslContext.fetchExists(
                MINECRAFT_SERVER_PLAYER,
                MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(playerUuid)
                        .and(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID.eq(serverId))
        );
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

        dslContext.deleteFrom(MINECRAFT_SERVER_FAVORITE)
                .where(MINECRAFT_SERVER_FAVORITE.MINECRAFT_SERVER_ID.in(serverIds))
                .and(MINECRAFT_SERVER_FAVORITE.PROFILE_ID.eq(profileId))
                .execute();
    }

    /// id избранных серверов профиля в порядке добавления.
    public List<Long> getFavoriteIds(Long profileId) {
        return dslContext.select(MINECRAFT_SERVER_FAVORITE.MINECRAFT_SERVER_ID)
                .from(MINECRAFT_SERVER_FAVORITE)
                .where(MINECRAFT_SERVER_FAVORITE.PROFILE_ID.eq(profileId))
                .orderBy(MINECRAFT_SERVER_FAVORITE.FAVORITED_AT, MINECRAFT_SERVER_FAVORITE.MINECRAFT_SERVER_ID)
                .fetch(MINECRAFT_SERVER_FAVORITE.MINECRAFT_SERVER_ID);
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
