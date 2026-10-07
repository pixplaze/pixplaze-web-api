package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerState;
import com.pixplaze.api.web.data.server.ObservedState;
import com.pixplaze.api.web.data.server.ServerHosts;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import com.pixplaze.api.web.exception.MinecraftServerUnavailableException;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.repository.MinecraftPlayerRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.service.server.MinecraftServerListingAssembler;
import com.pixplaze.api.web.service.server.MinecraftServerSnapshotStore;
import com.pixplaze.api.web.service.server.model.MinecraftServerListing;
import com.pixplaze.api.web.util.NullUtils;
import com.pixplaze.api.web.util.PagingUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class MinecraftServerService {
    private final MinecraftServerRepository minecraftServerRepository;
    private final MinecraftPlayerRepository minecraftPlayerRepository;
    private final MinecraftServerSnapshotStore minecraftServerSnapshotStore;
    private final MinecraftServerListingAssembler minecraftServerListingAssembler;
    private final MinecraftServerMonitoringService minecraftServerMonitoringService;

    /// Страница листинга из материализованного снапшота (без сетевого I/O; тир зависит от online/plugin).
    /// Порядок операций: фильтр по {@code search} → пагинация, чтобы offset/limit считались от совпадений.
    public List<MinecraftServerInfo> listServers(String search, int limit, int offset) {
        final var paging = PagingUtils.of(limit, offset);
        var stream = minecraftServerSnapshotStore.findAll().stream()
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
        return minecraftServerSnapshotStore.find(id)
                .map(minecraftServerListingAssembler::toServerInfo)
                .orElse(null);
    }

    /// Состояние набора серверов (веб-апп refresh, POST /servers/state); неизвестные id опускаются.
    public List<MinecraftServerStateInfo> getServerStates(Collection<Long> ids) {
        return ids.stream()
                .map(minecraftServerSnapshotStore::find)
                .flatMap(Optional::stream)
                .map(minecraftServerListingAssembler::toStateInfo)
                .toList();
    }

    /**
     * Heartbeat плагина ({@link MinecraftServerInfo#heartbeat}): берём только то, за что отвечает плагин.
     * Состояние пересобирается через {@link MinecraftServerStateInfo#heartbeat} — id сервера (из токена,
     * а не тела), списки игроков и прочие поля отбрасываются; плагины — в снапшот; лицензия — в БД, если
     * изменилась. Адреса, имя, motd, ядро и иконка из тела игнорируются: у них другие источники.
     * Часть плагина гаснет по TTL в ассемблере. No-op для снапшота, если сервера в нём ещё нет.
     */
    public void handleHeartbeat(Long serverId, MinecraftServerInfo heartbeat) {
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
        ).ifPresent(minecraftServerSnapshotStore::putBase);
    }

    /// Голос игрока за сервер (1..5). UPSERT по паре (сервер, игрок) → один голос на игрока,
    /// повторный вызов переголосовывает. Пересчитывает агрегат и кладёт его в снапшот, чтобы
    /// новое среднее подхватилось ближайшим publish-тиком (~5с), не дожидаясь base-sync (~5мин).
    @Transactional
    public void rate(Long serverId, Long profileId, int rating) {
        // Диапазон 1..5 валидируется на границе контроллера (@Min/@Max → 400 в ApiExceptionHandler).
        if (minecraftServerRepository.findById(serverId).isEmpty()) {
            throw new NotFoundException("Server '%d' not found".formatted(serverId));
        }
        minecraftServerRepository.upsertRating(serverId, profileId, rating);
        minecraftServerSnapshotStore.putRating(serverId, minecraftServerRepository.ratingAggregate(serverId));
    }

    /// Агрегаты рейтинга по всем серверам (для base-sync листинга).
    public Map<Long, ServerRatingAggregate> ratingAggregatesByServer() {
        return minecraftServerRepository.ratingAggregatesByServer();
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

    public Optional<MinecraftServer> findById(Long id) {
        return minecraftServerRepository.findById(id);
    }

    /// Создаёт сервер с адресами и строкой состояния в момент успешной регистрации. После коммита
    /// сервер сразу попадает в снапшот: в листинг — с ближайшим publish, в пинг — со следующим тиком.
    public MinecraftServer create(
            MinecraftServer server,
            MinecraftServerStateInfo.IntegrationStatus integrationStatus,
            Collection<MinecraftServerHostInfo> hosts
    ) {
        final var created = minecraftServerRepository.create(server, integrationStatus, hosts);
        afterCommit(() -> minecraftServerSnapshotStore.putServer(MinecraftServerListing.of(
                created,
                minecraftServerRepository.findHosts(created.getId()),
                integrationStatus,
                null,
                ServerRatingAggregate.EMPTY
        )));
        return created;
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
        afterCommit(() -> minecraftServerSnapshotStore.putHosts(serverId, minecraftServerRepository.findHosts(serverId)));
    }

    /// Лицензию (online-mode) сообщает сам сервер; пишется только изменение, снапшот — после коммита.
    public void updateLicense(Long serverId, Boolean isLicense) {
        minecraftServerRepository.updateLicense(serverId, isLicense)
                .ifPresent(updated -> afterCommit(() -> minecraftServerSnapshotStore.putBase(updated)));
    }

    /// Пакетно привязывает операторов; владелец помечается is_owner.
    public void linkOperators(Long serverId, Collection<UUID> operatorUuids, UUID ownerUuid) {
        minecraftServerRepository.linkOperators(serverId, operatorUuids, ownerUuid);
    }

    public boolean isBanned(Long serverId) {
        return minecraftServerRepository.isBanned(serverId);
    }

    /// Бан; снапшот после коммита — листинг показывает BANNED сразу, не дожидаясь base-sync.
    public void ban(Long serverId) {
        minecraftServerRepository.ban(serverId)
                .ifPresent(banned -> afterCommit(() -> minecraftServerSnapshotStore.putBase(banned)));
    }

    public void linkOperator(UUID playerUuid, Long serverId) {
        minecraftServerRepository.linkWithOperator(playerUuid, serverId);
    }

    /// Фиксирует членство вошедшего игрока на сервере (idempotent upsert; обновляет is_operator).
    public void linkPlayer(Long serverId, UUID playerUuid, boolean isOperator) {
        minecraftServerRepository.upsertPlayer(serverId, playerUuid, isOperator);
    }

    public void addFavorite(Long serverId, Long profileId) {
        minecraftServerRepository.addFavorite(serverId, profileId);
    }

    public void addFavorite(List<Long> serverIds, Long profileId) {
        minecraftServerRepository.addFavorite(serverIds, profileId);
    }

    public void removeFavorite(Long serverId, Long profileId) {
        minecraftServerRepository.removeFavorite(serverId, profileId);
    }

    public List<MinecraftServer> getFavorite(Long profileId) {
        return minecraftServerRepository.getFavorite(profileId);
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

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /// Пинг с отказом: недоступный сервер → 503.
    public MinecraftServerInfo requireOnline(String host, Integer port) {
        return pingServer(host, port).orElseThrow(minecraftServerUnavailableException(host, port));
    }

    private Supplier<MinecraftServerUnavailableException> minecraftServerUnavailableException(String host, Integer port) {
        return () ->  new MinecraftServerUnavailableException("Could not connect to Minecraft server: '%s:%s'!".formatted(host, port));
    }
}
