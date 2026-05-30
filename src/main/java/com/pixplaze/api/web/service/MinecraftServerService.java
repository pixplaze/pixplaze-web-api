package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerCoreInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.server.MinecraftServer;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.service.api.server.MinecraftServerApiService;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.util.*;

@Service
public class MinecraftServerService {
    private final MinecraftServerRepository minecraftServerRepository;
    private final Map<MinecraftServerInfo, MinecraftServerApiService> minecraftServerApiServiceMap;
    private final MinecraftServerPollingService minecraftServerPollingService;

    @Autowired
    public MinecraftServerService(MinecraftServerRepository minecraftServerRepository, MinecraftServerPollingService minecraftServerPollingService) {
        this.minecraftServerRepository = minecraftServerRepository;
        this.minecraftServerPollingService = minecraftServerPollingService;
        this.minecraftServerApiServiceMap = new HashMap<>();
    }

    @PostConstruct
    public void init() {
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
        MinecraftServer minecraftServer = minecraftServerPollingService.get(hostname);
        if (minecraftServer == null) {
            return null;
        }
        final var minecraftServerCoreInfo = new MinecraftServerCoreInfo(
                minecraftServer.getCore().getName(),
                minecraftServer.getCore().getVersion()
        );
        final var minecraftServerStateInfo = new MinecraftServerStateInfo(
                minecraftServer.getState().getTps(),
                minecraftServer.getState().getPing(),
                minecraftServer.getState().getUptime(),
                null,
                minecraftServer.getState().getState(),
                minecraftServer.getState().getPlayers()
        );

        return new MinecraftServerInfo(
                minecraftServer.getHost(),
                minecraftServer.getMotd(),
                minecraftServer.getLicense(),
                minecraftServer.getFavicon(),
                minecraftServer.getPorts(),
                minecraftServerCoreInfo,
                minecraftServerStateInfo,
                null,
                null
        );
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
}
