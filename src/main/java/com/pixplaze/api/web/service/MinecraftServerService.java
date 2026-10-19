package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerHeartbeatInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerHeartbeatResponse;
import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerState;
import com.pixplaze.api.web.data.server.ObservedState;
import com.pixplaze.api.web.data.server.ServerHosts;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import com.pixplaze.api.web.exception.MinecraftServerUnavailableException;
import com.pixplaze.api.web.exception.http.BadRequestException;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.exception.http.ServiceUnavailableException;
import com.pixplaze.api.web.repository.MinecraftPlayerRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.service.presence.MinecraftPresenceService;
import com.pixplaze.api.web.service.server.MinecraftServerListingAssembler;
import com.pixplaze.api.web.service.server.MinecraftServerSnapshotStore;
import com.pixplaze.api.web.service.server.event.MinecraftServerBanned;
import com.pixplaze.api.web.service.server.event.MinecraftServerChanged;
import com.pixplaze.api.web.service.server.event.MinecraftServerCreated;
import com.pixplaze.api.web.service.server.event.MinecraftServerHostsChanged;
import com.pixplaze.api.web.service.server.event.MinecraftServerRatingChanged;
import com.pixplaze.api.web.util.NullUtils;
import com.pixplaze.api.web.util.PagingUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MinecraftServerService {
    private final MinecraftServerRepository minecraftServerRepository;
    private final MinecraftPlayerRepository minecraftPlayerRepository;
    private final MinecraftServerSnapshotStore minecraftServerSnapshotStore;
    private final MinecraftServerListingAssembler minecraftServerListingAssembler;
    private final MinecraftServerMonitoringService minecraftServerMonitoringService;
    private final ApplicationEventPublisher eventPublisher;
    private final MinecraftPresenceService minecraftPresenceService;

    /// Страница листинга из материализованного снапшота (без сетевого I/O; тир зависит от online/plugin).
    /// Порядок операций: фильтр по {@code search} → пагинация, чтобы offset/limit считались от совпадений.
    public List<MinecraftServerInfo> listServers(String search, int limit, int offset) {
        final var paging = PagingUtils.of(limit, offset);
        var stream = readySnapshot().findAll().stream()
                .map(minecraftServerListingAssembler::toServerInfo);

        if (search != null && !search.isBlank()) {
            final var needle = search.toLowerCase();
            stream = stream.filter(info -> matches(info, needle));
        }

        return stream.skip(paging.from()).limit(paging.size()).toList();
    }

    /// Совпадение по адресам/name/description (регистронезависимо; null-поля пропускаются).
    private static boolean matches(MinecraftServerInfo info, String needle) {
        return containsAddress(info.hosts(), needle)
                || contains(info.name(), needle)
                || containsAvoidSpecialSymbols(info.motd(), needle)
                || contains(info.description(), needle);
    }

    private static boolean containsAddress(List<MinecraftServerHostInfo> hosts, String needle) {
        return hosts != null && hosts.stream().anyMatch(host -> contains(host.address(), needle));
    }

    private static boolean contains(String field, String needle) {
        return field != null && field.toLowerCase()
                .contains(needle);
    }

    private static boolean containsAvoidSpecialSymbols(String field, String needle) {
        return field != null && field.toLowerCase().replaceAll("[&§](\\w|\\d)", "") // Очистка от спец. символов (!влияет на производительность)
                .contains(needle);
    }

    /// Один сервер по id из снапшота ({@code null} → 404 на контроллере).
    public MinecraftServerInfo getServerInfo(Long id) {
        return readySnapshot().find(id)
                .map(minecraftServerListingAssembler::toServerInfo)
                .orElse(null);
    }

    /// Серверы по id из снапшота в том же виде, что и листинг, в порядке {@code ids}; неизвестные id опускаются.
    /// Сведения о серверах для API собираются только так: в строке БД нет адресов, состояния и рейтинга.
    public List<MinecraftServerInfo> getServerInfos(Collection<Long> ids) {
        final var snapshot = readySnapshot();
        return ids.stream()
                .map(snapshot::find)
                .flatMap(Optional::stream)
                .map(minecraftServerListingAssembler::toServerInfo)
                .toList();
    }

    /// Состояние набора серверов (веб-апп refresh, POST /servers/state); неизвестные id опускаются.
    public List<MinecraftServerStateInfo> getServerStates(Collection<Long> ids) {
        final var snapshot = readySnapshot();
        return ids.stream()
                .map(snapshot::find)
                .flatMap(Optional::stream)
                .map(minecraftServerListingAssembler::toStateInfo)
                .toList();
    }

    /// Снапшот для чтения. До прогрева он пуст, и пустой ответ выглядел бы как «серверов нет»
    /// (web-app сбросил бы выбранный сервер), поэтому до тех пор — 503.
    private MinecraftServerSnapshotStore readySnapshot() {
        if (!minecraftServerSnapshotStore.isReady()) {
            throw new ServiceUnavailableException("Server listing is warming up");
        }
        return minecraftServerSnapshotStore;
    }

    /**
     * Heartbeat плагина ({@link MinecraftServerHeartbeatInfo}): берём только то, за что отвечает плагин.
     * Состояние пересобирается через {@link MinecraftServerStateInfo#heartbeat} — id сервера (из токена,
     * а не тела), списки игроков и прочие поля отбрасываются; плагины — в снапшот; лицензия — в БД, если
     * изменилась. Адреса, имя, motd, ядро и иконка из тела игнорируются: у них другие источники.
     * Часть плагина гаснет по TTL в ассемблере. No-op для снапшота, если сервера в нём ещё нет.
     * Снимок игроков ({@code playersHash}/{@code players}) — в {@link MinecraftPresenceService}.
     */
    public MinecraftServerHeartbeatResponse handleHeartbeat(Long serverId, MinecraftServerHeartbeatInfo heartbeat) {
        final var state = heartbeat.state();
        if (state != null) {
            final var players = state.players();
            final var plugin = MinecraftServerStateInfo.builder(MinecraftServerStateInfo.heartbeat(
                            state.tps(),
                            state.ping(),
                            state.uptime(),
                            state.difficulty(),
                            players != null ? players.online() : null,
                            players != null ? players.max() : null))
                    .minecraftServerId(serverId)
                    .build();
            minecraftServerSnapshotStore.putPlugin(serverId, new ObservedState(plugin, Instant.now()));
        }

        updateLicense(serverId, heartbeat.isLicense());
        return new MinecraftServerHeartbeatResponse(minecraftPresenceService.accept(serverId, heartbeat));
    }

    /// Описание, которое web-api узнаёт пингом: пишет в БД только изменение и обновляет снапшот.
    public void updatePingDescription(Long serverId, MinecraftServerInfo pinged) {
        final var core = pinged.core();
        minecraftServerRepository.updatePingDescription(
                serverId,
                pinged.motd(),
                pinged.iconBase64(),
                core != null ? core.name() : null,
                core != null ? core.version() : null
        ).ifPresent(updated -> eventPublisher.publishEvent(new MinecraftServerChanged(updated)));
    }

    /// Голос игрока за сервер (1..5). UPSERT по паре (сервер, игрок) → один голос на игрока,
    /// повторный вызов переголосовывает. После коммита агрегат пересчитывается в снапшоте, и
    /// новое среднее подхватывается ближайшим publish-тиком (~5с), не дожидаясь base-sync (~5мин).
    @Transactional
    public void rate(Long serverId, Long profileId, int rating) {
        // Диапазон 1..5 валидируется на границе контроллера (@Min/@Max → 400 в ApiExceptionHandler).
        if (minecraftServerRepository.findById(serverId).isEmpty()) {
            throw new NotFoundException("Server '%d' not found".formatted(serverId));
        }
        minecraftServerRepository.upsertRating(serverId, profileId, rating);
        eventPublisher.publishEvent(new MinecraftServerRatingChanged(serverId));
    }

    /// Агрегаты рейтинга по всем серверам (для base-sync листинга).
    public Map<Long, ServerRatingAggregate> ratingAggregatesByServer() {
        return minecraftServerRepository.ratingAggregatesByServer();
    }

    public boolean isPlayerServerMember(UUID playerUuid, Long serverId) {
        return minecraftServerRepository.isPlayerServerMember(playerUuid, serverId);
    }

    public boolean isPlayerServerOperator(UUID playerUuid, Long serverId) {
        return minecraftServerRepository.isPlayerServerOperator(playerUuid, serverId);
    }

    public boolean isPlayerProfileServerOperator(Long profileId, Long serverId) {
        return minecraftServerRepository.isPlayerProfileServerOperator(profileId, serverId);
    }

    /// Все залистингованные серверы (база из БД) — источник правды для листинга/рефрешера.
    public List<MinecraftServer> findAllListed() {
        return minecraftServerRepository.findAllListed();
    }

    /// Адреса всех серверов, сгруппированные по серверу — для base-sync листинга.
    public Map<Long, ServerHosts> findAllHosts() {
        return minecraftServerRepository.findAllHosts();
    }

    /// Последнее сохранённое состояние всех серверов — для base-sync листинга.
    public Map<Long, MinecraftServerState> findAllStates() {
        return minecraftServerRepository.findAllStates();
    }

    /// Зарегистрированный сервер (ACTIVE); заявки и закрытые не находятся.
    public Optional<MinecraftServer> findById(Long id) {
        return minecraftServerRepository.findById(id);
    }

    /// Сервер в любом статусе — для заявок и регистрации.
    public Optional<MinecraftServer> findAnyById(Long id) {
        return minecraftServerRepository.findAnyById(id);
    }

    /// Регистрирует заявку (PENDING → ACTIVE) со строкой состояния. После коммита сервер сразу попадает в
    /// снапшот: в листинг — с ближайшим publish, в пинг — со следующим тиком. Пусто — сервер уже не заявка.
    public Optional<MinecraftServer> activate(Long serverId, MinecraftServerStateInfo.IntegrationStatus integrationStatus) {
        final var activated = minecraftServerRepository.activate(serverId, integrationStatus);
        activated.ifPresent(server -> eventPublisher.publishEvent(new MinecraftServerCreated(server, integrationStatus)));
        return activated;
    }

    /// Игровой адрес (HOST) сервера, если сервер существует.
    public Optional<MinecraftServerHostInfo> findGameHost(Long serverId) {
        return minecraftServerRepository.findHosts(serverId).gameHost();
    }

    /// Записывает адреса и после коммита кладёт полный их набор в снапшот: листинг и пинг видят
    /// новый адрес сразу, не дожидаясь base-sync, а откат транзакции стор не затрагивает.
    public void upsertHosts(Long serverId, Collection<MinecraftServerHostInfo> hosts) {
        if (NullUtils.isNullOrEmpty(hosts)) {
            return;
        }

        minecraftServerRepository.upsertHosts(serverId, hosts);
        eventPublisher.publishEvent(new MinecraftServerHostsChanged(serverId));
    }

    /// Лицензию (online-mode) сообщает сам сервер; пишется только изменение, снапшот — после коммита.
    public void updateLicense(Long serverId, Boolean isLicense) {
        minecraftServerRepository.updateLicense(serverId, isLicense)
                .ifPresent(updated -> eventPublisher.publishEvent(new MinecraftServerChanged(updated)));
    }

    /// Пакетно привязывает операторов; владельца задаёт {@code owner_profile_id}, а не эта связь.
    public void linkOperators(Long serverId, Collection<UUID> operatorUuids) {
        minecraftServerRepository.linkOperators(serverId, operatorUuids);
    }

    /// Перманентный бан сервера по его identity: отметка {@code banned_at}; в той же транзакции
    /// слушатели {@link MinecraftServerBanned} отзывают его refresh-токены, так что дальнейшая
    /// авторизация и ротация для этого id невозможны, с любого host/имени. Снапшот — после коммита:
    /// листинг показывает BANNED сразу, не дожидаясь base-sync. Повторный бан — no-op.
    @Transactional
    public void ban(Long serverId) {
        minecraftServerRepository.ban(serverId).ifPresent(banned -> {
            eventPublisher.publishEvent(new MinecraftServerBanned(banned.getId()));
            eventPublisher.publishEvent(new MinecraftServerChanged(banned));
        });
    }

    public void linkOperator(UUID playerUuid, Long serverId) {
        minecraftServerRepository.linkWithOperator(playerUuid, serverId);
    }

    /// Фиксирует членство вошедшего игрока на сервере (idempotent upsert; обновляет is_operator).
    public void linkPlayer(Long serverId, UUID playerUuid, boolean isOperator) {
        minecraftServerRepository.upsertPlayer(serverId, playerUuid, isOperator);
    }

    /// Основной игрок профиля на сервере: {@code sub} токена доступа к плагину.
    public Optional<UUID> findPrimaryPlayer(Long profileId, Long serverId) {
        return minecraftServerRepository.findPrimaryPlayer(profileId, serverId);
    }

    /// Назначает основного игрока, если у профиля на сервере его нет (после привязки или отвязки игрока).
    public void ensurePrimaryPlayer(Long profileId, Long serverId) {
        minecraftServerRepository.ensurePrimaryPlayer(profileId, serverId);
    }

    /// Переключатель «основной» в настройках. {@code false} — игрок не принадлежит профилю или не на сервере.
    public boolean setPrimaryPlayer(Long profileId, Long serverId, UUID playerUuid) {
        return minecraftServerRepository.setPrimaryPlayer(profileId, serverId, playerUuid);
    }

    public void addFavorite(Long serverId, Long profileId) {
        minecraftServerRepository.addFavorite(serverId, profileId);
    }

    /// Добавляет серверы в избранное профиля; уже добавленные пропускаются. Все или ничего:
    /// неизвестный id → 404, и ни один сервер из запроса не добавляется. Проверка — до вставки,
    /// а не по нарушению FK: оно логируется jOOQ как ERROR.
    public void addFavorite(List<Long> serverIds, Long profileId) {
        if (serverIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("Server ids must not be empty!");
        }
        final var ids = Set.copyOf(serverIds);
        if (minecraftServerRepository.countExisting(ids) != ids.size()) {
            throw new NotFoundException("Some of servers %s not found".formatted(serverIds));
        }
        minecraftServerRepository.addFavorite(List.copyOf(ids), profileId);
    }

    public void removeFavorite(Long serverId, Long profileId) {
        minecraftServerRepository.removeFavorite(serverId, profileId);
    }

    /// Убирает серверы из избранного профиля; отсутствующие в избранном пропускаются.
    public void removeFavorite(List<Long> serverIds, Long profileId) {
        if (serverIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("Server ids must not be empty!");
        }
        minecraftServerRepository.removeFavorite(Set.copyOf(serverIds), profileId);
    }

    /// Избранные серверы профиля в порядке добавления.
    public List<MinecraftServerInfo> getFavorite(Long profileId) {
        return getServerInfos(minecraftServerRepository.getFavoriteIds(profileId));
    }

    public Optional<MinecraftServerInfo> pingServer(
            @Size(min = 1, max = 128, message = "Хост должен содержать от 1 до 128 символов")
            @NotBlank(message = "Хост не может быть пустым")
            String host,
            Integer port
    ) {
        try {
            return Optional.of(minecraftServerMonitoringService.ping(host, port));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /// Пинг с отказом: недоступный сервер → 503.
    public MinecraftServerInfo requireOnline(String host, Integer port) {
        return pingServer(host, port).orElseThrow(minecraftServerUnavailableException(host, port));
    }

    private Supplier<MinecraftServerUnavailableException> minecraftServerUnavailableException(String host, Integer port) {
        return () ->  new MinecraftServerUnavailableException("Could not connect to Minecraft server: '%s:%s'!".formatted(host, port));
    }
}
