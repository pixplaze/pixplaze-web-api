package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.MinecraftServerApi;
import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.ext.data.plugin.MinecraftPluginInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.ext.data.server.PixplazeServerInfo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MinecraftServerApiService implements MinecraftServerApi {

    private final RestClient restClient;

    public MinecraftServerApiService(PixplazeServerInfo pixplazeServerInfo) {
        this.restClient = RestClient.create(buildApiBaseUrl(pixplazeServerInfo));
    }

    @Override
    public MinecraftServerInfo getServerInfo(boolean fetchThumbnail) {
        final var uri = UriComponentsBuilder.fromUriString("/server")
                .queryParam("thumbnail", fetchThumbnail)
                .toUriString();
        return restClient.get().uri(uri)
                .retrieve()
                .body(MinecraftServerInfo.class);
    }

    @Override
    public MinecraftServerStateInfo getServerState() {
        return restClient.get().uri("/server/state")
                .retrieve()
                .body(MinecraftServerStateInfo.class);
    }

    @Override
    public List<MinecraftPluginInfo> getInstalledPlugins() {
        return restClient.get().uri("/server/plugins/installed")
                .retrieve()
                .body(ParameterizedTypeReference.forType(MinecraftPluginInfo.class));
    }

    @Override
    public List<MinecraftPluginInfo> getEnabledPlugins() {
        return restClient.get().uri("/server/plugins/enabled")
                .retrieve()
                .body(ParameterizedTypeReference.forType(MinecraftPluginInfo.class));
    }

    @Override
    public Set<MinecraftPlayerInfo> getOnlinePlayers() {
        return restClient.get().uri("/server/players?view={view}")
                .attribute("view", "online")
                .retrieve()
                .body(ParameterizedTypeReference.forType(MinecraftPlayerInfo.class));
    }

    @Override
    public Set<MinecraftPlayerInfo> getOfflinePlayers() {
        return restClient.get().uri("/server/players?view={view}")
                .attribute("view", "offline")
                .retrieve()
                .body(ParameterizedTypeReference.forType(MinecraftPlayerInfo.class));
    }

    @Override
    public Set<MinecraftPlayerInfo> getBannedPlayers() {
        return restClient.get().uri("/server/players?view={view}")
                .attribute("view", "banned")
                .retrieve()
                .body(ParameterizedTypeReference.forType(MinecraftPlayerInfo.class));
    }

    @Override
    public Set<MinecraftPlayerInfo> getWhitelistedPlayers() {
        return restClient.get().uri("/server/players?view={view}")
                .attribute("view", "whitelisted")
                .retrieve()
                .body(ParameterizedTypeReference.forType(MinecraftPlayerInfo.class));
    }

    @Override
    public Set<MinecraftPlayerInfo> getOpPlayers() {
        return restClient.get().uri("/server/players?view={view}")
                .attribute("view", "operators")
                .retrieve()
                .body(ParameterizedTypeReference.forType(MinecraftPlayerInfo.class));
    }

    @Override
    public Set<MinecraftPlayerInfo> getAllPlayers() {
        return restClient.get().uri("/server/players?view={view}")
                .attribute("view", "all")
                .retrieve()
                .body(ParameterizedTypeReference.forType(MinecraftPlayerInfo.class));
    }

    private String buildApiBaseUrl(PixplazeServerInfo pixplazeServerInfo) {
        final var hostPrefix = pixplazeServerInfo.host();
        final var portSuffix = Objects.nonNull(pixplazeServerInfo.port()) ? ":" + pixplazeServerInfo.port() : "";
        return "http://" + hostPrefix + portSuffix;
    }
}
