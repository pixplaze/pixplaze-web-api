package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerCoreInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.server.MinecraftServerStatus;
import com.pixplaze.api.web.data.server.RawMinecraftServer;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.repository.MinecraftPlayerRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.service.api.server.MinecraftServerApiService;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MinecraftServerService {
    private final MinecraftServerRepository minecraftServerRepository;
    private final Map<MinecraftServerInfo, MinecraftServerApiService> minecraftServerApiServiceMap;
    private final MinecraftServerPollingService minecraftServerPollingService;
    private final MinecraftPlayerRepository minecraftPlayerRepository;

    @Autowired
    public MinecraftServerService(MinecraftServerRepository minecraftServerRepository, MinecraftServerPollingService minecraftServerPollingService, MinecraftPlayerRepository minecraftPlayerRepository) {
        this.minecraftServerRepository = minecraftServerRepository;
        this.minecraftServerPollingService = minecraftServerPollingService;
        this.minecraftServerApiServiceMap = new HashMap<>();
        this.minecraftPlayerRepository = minecraftPlayerRepository;
    }

    @PostConstruct
    public void init() {
// Вешает приложение при запуске
//        final var inetSocketAddressList = minecraftServerRepository.getPixplazeServerList().stream()
//                .map(ms -> new InetSocketAddress(ms.host(), ms.ports().java()))
//                .toList();
//        minecraftServerPollingService.push(inetSocketAddressList);
    }

    public List<MinecraftServerInfo> getPixplazeServerList(boolean fetchThumbnail) {
        final var pixplazeServerList = minecraftServerRepository.getPixplazeServerList();

        return pixplazeServerList.parallelStream()
                .map(s -> fillWithMinecraftServerInfo(s, fetchThumbnail))
                .filter(Objects::nonNull)
                .toList();
    }

    /// Retrieves minecraft server info
    public MinecraftServerInfo getServerInfo(Integer id, boolean fetchThumbnail) {
        final var pixplazeServerInfo = minecraftServerRepository.getPixplazeServerInfoById(id);

        Optional.ofNullable(pixplazeServerInfo).orElseThrow(NotFoundException::new);

        return fillWithMinecraftServerInfo(pixplazeServerInfo, fetchThumbnail);
    }

    @SneakyThrows
    private MinecraftServerInfo fillWithMinecraftServerInfo(MinecraftServerInfo pixplazeServerInfo, boolean fetchThumbnail) {
//        try {
//            final var serverApi = getServerApi(pixplazeServerInfo);
//            return serverApi.getServerInfo(fetchThumbnail);
//        } catch (ResourceAccessException | HttpServerErrorException.InternalServerError e) {
            return requestMinecraftServer(pixplazeServerInfo.host());
//        }
    }

    public MinecraftServerInfo requestMinecraftServer(String hostname) {
        RawMinecraftServer rawMinecraftServer = minecraftServerPollingService.get(hostname);
        if (rawMinecraftServer == null) {
            return null;
        }
        final var minecraftServerCoreInfo = new MinecraftServerCoreInfo(
                rawMinecraftServer.getCore().getName(),
                rawMinecraftServer.getCore().getVersion()
        );
        final var minecraftServerStateInfo = new MinecraftServerStateInfo(
                rawMinecraftServer.getState().getTps(),
                rawMinecraftServer.getState().getPing(),
                rawMinecraftServer.getState().getUptime(),
                null,
                rawMinecraftServer.getState().getState(),
                rawMinecraftServer.getState().getPlayers()
        );

        return new MinecraftServerInfo(
                null,
                rawMinecraftServer.getHost(),
                rawMinecraftServer.getMotd(),
                rawMinecraftServer.getLicense(),
                rawMinecraftServer.getFavicon(),
                rawMinecraftServer.getMotd(),
                rawMinecraftServer.getPorts(),
                minecraftServerCoreInfo,
                minecraftServerStateInfo,
                null,
                null
        );
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

    private MinecraftServerApiService getServerApi(MinecraftServerInfo pixplazeServerInfo) {
        var serverApi = minecraftServerApiServiceMap.get(Objects.requireNonNull(pixplazeServerInfo, "Pixplaze server must not be null!"));

        if (Objects.nonNull(serverApi)) {
            return serverApi;
        }

        serverApi = new MinecraftServerApiService(pixplazeServerInfo);
        minecraftServerApiServiceMap.put(pixplazeServerInfo, serverApi);

        return serverApi;
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
