package com.pixplaze.api.web.service.server;

import com.pixplaze.api.ext.data.server.PixplazeServerInfo;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class PixplazeApiStoreService {
    private final Map<String, RestClient> restClients;

    public PixplazeApiStoreService() {
        this.restClients = new HashMap<>();
    }

    public RestClient getRestClient(PixplazeServerInfo pixplazeServerInfo) {
        return createOrGetRestClient(pixplazeServerInfo);
    }

    private String buildApiBaseUrl(PixplazeServerInfo pixplazeServerInfo) {
        final var hostPrefix = pixplazeServerInfo.host();
        final var portSuffix = Objects.nonNull(pixplazeServerInfo.port()) ? ":" + pixplazeServerInfo.port() : "";
        return "http://" + hostPrefix + portSuffix;
    }

    private RestClient createOrGetRestClient(PixplazeServerInfo pixplazeServerInfo) {
        final var apiBaseUrl = buildApiBaseUrl(pixplazeServerInfo);
        var restClient = restClients.get(apiBaseUrl);

        if (Objects.nonNull(restClient)) {
            return restClient;
        }

        restClient = createRestClient(apiBaseUrl);
        restClients.put(apiBaseUrl, restClient);

        return restClient;
    }

    private RestClient createRestClient(String apiBaseUrl) {
        return RestClient.builder()
                .baseUrl(apiBaseUrl)
                .build();
    }
}
