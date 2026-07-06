package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.dto.MinecraftServerHeartbeatRequest;
import com.pixplaze.api.web.data.server.MinecraftServerSnapshot;
import com.pixplaze.api.web.data.server.MinecraftServerStatus;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import com.pixplaze.api.web.exception.http.BadRequestException;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.repository.MinecraftPlayerRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.service.server.MinecraftServerListingAssembler;
import com.pixplaze.api.web.service.server.MinecraftServerSnapshotStore;
import com.pixplaze.api.web.util.PagingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MinecraftServerService {
    private final MinecraftServerRepository minecraftServerRepository;
    private final MinecraftPlayerRepository minecraftPlayerRepository;
    private final MinecraftServerSnapshotStore minecraftServerSnapshotStore;
    private final MinecraftServerListingAssembler minecraftServerListingAssembler;

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
                || contains(info.description(), needle);
    }

    private static boolean contains(String field, String needle) {
        return field != null && field.toLowerCase().contains(needle);
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
    public void rate(Long serverId, UUID playerUuid, int rating) {
        if (rating < 1 || rating > 5) {
            throw new BadRequestException("Rating must be between 1 and 5");
        }
        if (minecraftServerRepository.findById(serverId).isEmpty()) {
            throw new NotFoundException("Server '%d' not found".formatted(serverId));
        }
        minecraftServerRepository.upsertRating(serverId, playerUuid, rating);
        minecraftServerSnapshotStore.putRating(serverId, minecraftServerRepository.ratingAggregate(serverId));
    }

    /// Агрегаты рейтинга по всем серверам (для base-sync листинга).
    public Map<Long, ServerRatingAggregate> ratingAggregatesByServer() {
        return minecraftServerRepository.ratingAggregatesByServer();
    }

    public MinecraftServer createIfNotExist(
            MinecraftServer minecraftServer
    ) {
        return minecraftServerRepository.createIfNotExist(minecraftServer);
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
    public MinecraftServer createActive(MinecraftServer server) {
        return minecraftServerRepository.createActive(server);
    }

    /// Пакетно привязывает операторов; владелец помечается is_owner.
    public void linkOperators(Long serverId, Collection<UUID> operatorUuids, UUID ownerUuid) {
        minecraftServerRepository.linkOperators(serverId, operatorUuids, ownerUuid);
    }

    public Optional<MinecraftServerStatus> getStatus(Long serverId) {
        return minecraftServerRepository.getStatus(serverId);
    }

    public void markActive(Long serverId) {
        minecraftServerRepository.setStatus(serverId, MinecraftServerStatus.ACTIVE);
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
}
