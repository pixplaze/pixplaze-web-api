package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.dto.MinecraftServerHeartbeatRequest;
import com.pixplaze.api.web.data.server.MinecraftServerStatus;
import com.pixplaze.api.web.data.server.PluginSnapshot;
import com.pixplaze.api.web.repository.MinecraftPlayerRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.service.server.ServerListingAssembler;
import com.pixplaze.api.web.service.server.ServerSnapshotStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MinecraftServerService {
    private final MinecraftServerRepository minecraftServerRepository;
    private final MinecraftPlayerRepository minecraftPlayerRepository;
    private final ServerSnapshotStore serverSnapshotStore;
    private final ServerListingAssembler serverListingAssembler;

    /// Страница листинга из материализованного снапшота (без сетевого I/O; тир зависит от online/plugin).
    public List<MinecraftServerInfo> listServers(int limit, int offset) {
        final var all = serverSnapshotStore.findAll();
        final var from = Math.max(0, offset);
        if (from >= all.size()) {
            return List.of();
        }
        final var size = Math.min(Math.max(limit, 1), 100);
        final var to = Math.min(from + size, all.size());
        return all.subList(from, to).stream()
                .map(serverListingAssembler::toServerInfo)
                .toList();
    }

    /// Один сервер по id из снапшота ({@code null} → 404 на контроллере).
    public MinecraftServerInfo getServerInfo(Long id) {
        return serverSnapshotStore.find(id)
                .map(serverListingAssembler::toServerInfo)
                .orElse(null);
    }

    /// Только online-обновляемая часть для набора серверов (веб-апп refresh, POST /servers/state).
    public Map<Long, MinecraftServerStateInfo> getServerStates(Collection<Long> ids) {
        final var result = new LinkedHashMap<Long, MinecraftServerStateInfo>();
        for (final var id : ids) {
            serverSnapshotStore.find(id).ifPresent(listing -> result.put(id, serverListingAssembler.toStateInfo(listing)));
        }
        return result;
    }

    /// Tier-3 push: сервер-плагин прислал heartbeat → кладём в снапшот (протухание — по TTL в ассемблере).
    /// No-op, если сервер ещё не в снапшоте (появится после ближайшего base-sync).
    public void handleHeartbeat(Long serverId, MinecraftServerHeartbeatRequest request) {
        final var snapshot = new PluginSnapshot(
                request.tps(),
                request.uptimeMillis(),
                request.difficulty(),
                request.plugins(),
                request.metadata(),
                Instant.now()
        );
        serverSnapshotStore.putPlugin(serverId, snapshot);
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
