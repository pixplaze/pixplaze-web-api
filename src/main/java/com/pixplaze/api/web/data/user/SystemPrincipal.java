package com.pixplaze.api.web.data.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Субъект системного токена: сам web-api, который обращается к REST плагина конкретного сервера
 * ({@code MinecraftServerApiService}). Устройства за ним нет ({@code src = SAD}); web-api такие
 * токены от клиентов не принимает.
 */
@Getter
@Setter
@NoArgsConstructor
public class SystemPrincipal extends ClientPrincipal {

    /// {@code sub} системного токена.
    public static final String NAME = "pixplaze-web-api";

    private Long minecraftServerId;

    @Override
    public String getName() {
        return NAME;
    }
}
