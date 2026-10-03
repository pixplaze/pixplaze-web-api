package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerBid;
import com.pixplaze.api.web.data.dto.MinecraftServerBidResponse;
import com.pixplaze.api.web.data.dto.MinecraftServerHeartbeatRequest;
import com.pixplaze.api.web.data.server.MinecraftServerSnapshot;
import com.pixplaze.api.web.data.server.MinecraftServerStatus;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.exception.MinecraftServerUnavailableException;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.mapper.MinecraftServerMapper;
import com.pixplaze.api.web.repository.MinecraftPlayerRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.service.server.MinecraftServerListingAssembler;
import com.pixplaze.api.web.service.server.MinecraftServerSnapshotStore;
import com.pixplaze.api.web.util.PagingUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final MinecraftServerMapper minecraftServerMapper;
    private final MinecraftServerBidService minecraftServerBidService;
    private final VoucherCodeService voucherCodeService;

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

    /// Совпадение по host/name/description (регистронезависимо; null-поля пропускаются).
    private static boolean matches(MinecraftServerInfo info, String needle) {
        return contains(info.host(), needle)
                || contains(info.name(), needle)
                || containsAvoidSpecialSymbols(info.motd(), needle)
                || contains(info.description(), needle);
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

    /// Только online-обновляемая часть для набора серверов (веб-апп refresh, POST /servers/state).
    public Map<Long, MinecraftServerStateInfo> getServerStates(Collection<Long> ids) {
        final var result = new LinkedHashMap<Long, MinecraftServerStateInfo>();
        for (final var id : ids) {
            minecraftServerSnapshotStore.find(id).ifPresent(listing -> result.put(id, minecraftServerListingAssembler.toStateInfo(listing)));
        }
        return result;
    }

    /// Tier-3 push: сервер-плагин прислал heartbeat → кладём в снапшот (протухание — по TTL в ассемблере).
    /// No-op, если сервер ещё не в снапшоте (появится после ближайшего base-sync).
    public void handleHeartbeat(Long serverId, MinecraftServerHeartbeatRequest request) {
        final var snapshot = new MinecraftServerSnapshot.Plugin(
                request.tps(),
                request.uptimeMillis(),
                request.difficulty(),
                request.plugins(),
                request.metadata(),
                Instant.now()
        );
        minecraftServerSnapshotStore.putPlugin(serverId, snapshot);
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

    /// Адреса для Tier-2 пинга (host + Java-порт) — для рефрешера.
    public List<com.pixplaze.api.web.data.server.ServerPingTarget> findPingTargets() {
        return minecraftServerRepository.findPingTargets();
    }

    public Optional<MinecraftServer> findByHost(String host) {
        return minecraftServerRepository.findByHost(host);
    }

    public Optional<MinecraftServer> findById(Long id) {
        return minecraftServerRepository.findById(id);
    }

    /// Создаёт сервер в статусе ACTIVE в момент успешной регистрации.
    public MinecraftServer create(MinecraftServer server) {
        return minecraftServerRepository.create(server, MinecraftServerStatus.ONLINE);
    }

    public MinecraftServer create(MinecraftServer server, MinecraftServerStatus minecraftServerStatus) {
        return minecraftServerRepository.create(server, minecraftServerStatus);
    }

    /// Пакетно привязывает операторов; владелец помечается is_owner.
    public void linkOperators(Long serverId, Collection<UUID> operatorUuids, UUID ownerUuid) {
        minecraftServerRepository.linkOperators(serverId, operatorUuids, ownerUuid);
    }

    public Optional<MinecraftServerStatus> getStatus(Long serverId) {
        return minecraftServerRepository.getStatus(serverId);
    }

    public void markActive(Long serverId) {
        minecraftServerRepository.setStatus(serverId, MinecraftServerStatus.ONLINE);
    }

    public void markBanned(Long serverId) {
        minecraftServerRepository.setStatus(serverId, MinecraftServerStatus.BANNED);
    }

    public void updateHost(Long serverId, String host) {
        minecraftServerRepository.updateHost(serverId, host);
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

    @Transactional
    public MinecraftServerBidResponse registerOnlineServer(ApplicationClientPrincipal clientPrincipal, String serverName, String serverHost, Integer serverPort) {
        final var minecraftServerSnapshot = pingServer(serverHost, serverPort).orElseThrow(this.minecraftServerUnavailableException(serverHost, serverPort));
        final var minecraftServer = new MinecraftServer()
                .setName(serverName)
                .setHost(serverHost);

        create(minecraftServer);

        return new MinecraftServerBidResponse(
                null,
                null,
                null,
                MinecraftServerInfo.preview(serverName, serverHost, serverPort, minecraftServerSnapshot.iconBase64())
        );
    }

    @Transactional
    public MinecraftServerBidResponse registerPluginServer(ApplicationClientPrincipal clientPrincipal, String serverName, String serverHost, Integer serverPort) {
        final var minecraftServerSnapshot = pingServer(serverHost, serverPort).orElseThrow(this.minecraftServerUnavailableException(serverHost, serverPort));
        final var voucherCode = voucherCodeService.issue(VoucherCodeType.INVITE_MINECRAFT_SERVER, 1);
        final var minecraftServerBid = new MinecraftServerBid()
                .setName(serverName)
                .setHost(serverHost)
                .setVoucherCodeId(voucherCode.getId())
                .setProfileId(clientPrincipal.getId());
        final var minecraftServerBidId = minecraftServerBidService.create(minecraftServerBid).getId();
        return new MinecraftServerBidResponse(
                minecraftServerBidId,
                null,
                voucherCode.getCode(),
                MinecraftServerInfo.preview(serverName, serverHost, serverPort, minecraftServerSnapshot.iconBase64())
        );
    }

    @Transactional
    public MinecraftServerBidResponse createBid(ApplicationClientPrincipal clientPrincipal, String serverName, String serverHost, Integer serverPort, Boolean serverIntegration) {
        if (serverIntegration) {
            return registerOnlineServer(clientPrincipal, serverName, serverHost, serverPort);
        }

        return registerPluginServer(clientPrincipal, serverName, serverHost, serverPort);
    }

    public Optional<MinecraftServerSnapshot.Online> pingServer(
            @Size(min = 1, max = 128, message = "Хост должен содержать от 1 до 128 символов")
            @NotBlank(message = "Хост не может быть пустым")
            String host,
            Integer port
    ) {
        try {
            return Optional.of(minecraftServerMonitoringService.pingOnline(host, port));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Supplier<MinecraftServerUnavailableException> minecraftServerUnavailableException(String host, Integer port) {
        return () ->  new MinecraftServerUnavailableException("Could not connect to Minecraft server: '%s:%s'!".formatted(host, port));
    }
}
