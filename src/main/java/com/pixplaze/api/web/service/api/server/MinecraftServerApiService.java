package com.pixplaze.api.web.service.api.server;

import com.pixplaze.api.ext.MinecraftServerApi;
import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.ext.data.plugin.MinecraftPluginInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.exception.MinecraftServerUnavailableException;
import com.pixplaze.api.web.service.auth.SystemAccessTokenService;
import com.pixplaze.api.web.util.AddressUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/// Клиент REST плагина одного сервера. Каждый запрос несёт свежий системный токен web-api
/// ({@link SystemAccessTokenService}: {@code RSYS}, {@code src = SAD}, {@code aud} — этот сервер, минута
/// жизни): токен выпускается заново на каждый запрос, без кеша.
public class MinecraftServerApiService implements MinecraftServerApi {

    private static final ParameterizedTypeReference<List<MinecraftPlayerInfo>> PLAYER_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<MinecraftPluginInfo>> PLUGIN_LIST = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public MinecraftServerApiService(MinecraftServerInfo pixplazeServerInfo, SystemAccessTokenService systemAccessTokenService) {
        final var serverId = Objects.requireNonNull(pixplazeServerInfo.id(), "Server 'id' must not be null!");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(1));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(buildApiBaseUrl(pixplazeServerInfo))
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(systemAccessTokenService.issueFor(serverId));
                    return execution.execute(request, body);
                })
                .build();
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
                .body(PLUGIN_LIST);
    }

    @Override
    public List<MinecraftPluginInfo> getEnabledPlugins() {
        return restClient.get().uri("/server/plugins/enabled")
                .retrieve()
                .body(PLUGIN_LIST);
    }

    @Override
    public List<MinecraftPlayerInfo> getOnlinePlayers() {
        return restClient.get().uri("/server/players?view={view}", "online")
                .retrieve()
                .body(PLAYER_LIST);
    }

    @Override
    public List<MinecraftPlayerInfo> getOfflinePlayers() {
        return restClient.get().uri("/server/players?view={view}", "offline")
                .retrieve()
                .body(PLAYER_LIST);
    }

    @Override
    public List<MinecraftPlayerInfo> getBannedPlayers() {
        return restClient.get().uri("/server/players?view={view}", "banned")
                .retrieve()
                .body(PLAYER_LIST);
    }

    @Override
    public List<MinecraftPlayerInfo> getWhitelistedPlayers() {
        return restClient.get().uri("/server/players?view={view}", "whitelisted")
                .retrieve()
                .body(PLAYER_LIST);
    }

    @Override
    public List<MinecraftPlayerInfo> getOpPlayers() {
        return restClient.get().uri("/server/players?view={view}", "operators")
                .retrieve()
                .body(PLAYER_LIST);
    }

    @Override
    public List<MinecraftPlayerInfo> getAllPlayers() {
        return restClient.get().uri("/server/players?view={view}", "all")
                .retrieve()
                .body(PLAYER_LIST);
    }

    /// База API плагина — адрес типа API; без него к плагину не обратиться. По https: системный токен не должен
    /// идти открытым текстом, а TLS перед плагином и так нужен браузерам. Локальный плагин разработки — по http.
    static String buildApiBaseUrl(MinecraftServerInfo pixplazeServerInfo) {
        final var api = pixplazeServerInfo.host(MinecraftServerHostInfo.Type.API)
                .orElseThrow(() -> new MinecraftServerUnavailableException(pixplazeServerInfo));
        final var scheme = AddressUtils.isLoopback(api.address()) ? "http://" : "https://";
        return scheme + AddressUtils.hostAndPort(api.address(), api.port());
    }
}
