package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.PixplazeServerInfo;
import com.pixplaze.api.web.exception.NotFoundException;
import com.pixplaze.api.web.repository.ServerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ServerService {
    private final ServerRepository serverRepository;
    private final Map<PixplazeServerInfo, MinecraftServerApiService> minecraftServerApiServiceMap;

    @Autowired
    public ServerService(ServerRepository serverRepository) {
        this.serverRepository = serverRepository;
        this.minecraftServerApiServiceMap = new HashMap<>();
    }

    public List<PixplazeServerInfo> getPixplazeServerList(boolean fetchThumbnail) {
        final var pixplazeServerList = serverRepository.getPixplazeServerList();

        return pixplazeServerList.parallelStream()
                .map(s -> fillWithMinecraftServerInfo(s, fetchThumbnail))
                .toList();
    }

    public PixplazeServerInfo getServerInfo(Integer id, boolean fetchThumbnail) {
        final var pixplazeServerInfo = serverRepository.getPixplazeServerInfoById(id);

        Optional.ofNullable(pixplazeServerInfo).orElseThrow(NotFoundException::new);

        return fillWithMinecraftServerInfo(pixplazeServerInfo, fetchThumbnail);
    }

    private PixplazeServerInfo fillWithMinecraftServerInfo(PixplazeServerInfo pixplazeServerInfo, boolean fetchThumbnail) {
        final var serverApi = getServerApi(pixplazeServerInfo);
        final var minecraftServerInfo = serverApi.getServerInfo(fetchThumbnail);

        return new PixplazeServerInfo(
                pixplazeServerInfo.host(),
                pixplazeServerInfo.port(),
                minecraftServerInfo
        );
    }

    private MinecraftServerApiService getServerApi(PixplazeServerInfo pixplazeServerInfo) {
        var serverApi = minecraftServerApiServiceMap.get(Objects.requireNonNull(pixplazeServerInfo, "Pixplaze server must not be null!"));

        if (Objects.nonNull(serverApi)) {
            return serverApi;
        }

        serverApi = new MinecraftServerApiService(pixplazeServerInfo);
        minecraftServerApiServiceMap.put(pixplazeServerInfo, serverApi);

        return serverApi;
    }
}
