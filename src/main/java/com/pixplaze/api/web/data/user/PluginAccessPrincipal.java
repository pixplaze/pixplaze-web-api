package com.pixplaze.api.web.data.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Субъект токена доступа к плагину: профиль на конкретном сервере. Токен выпускает web-api обменом
 * токена профиля ({@code POST /servers/{id}/token}), проверяет только плагин — web-api такие токены
 * не принимает ({@code aud} у них — сервер, а не шлюз).
 *
 * <p>{@code playerUuid} — основной игрок профиля на сервере ({@code sub} токена): по нему плагин
 * проверяет права в игре. {@code null} — у профиля на сервере нет игрока (владелец без игрока).
 * {@code playerName} — имя этого игрока: плагин подписывает им сообщения из веб-чата.
 */
@Getter
@Setter
@NoArgsConstructor
public class PluginAccessPrincipal extends ClientPrincipal {
    private Long profileId;
    private Long minecraftServerId;
    private UUID playerUuid;
    private String playerName;

    @Override
    public String getName() {
        return playerUuid == null ? null : playerUuid.toString();
    }
}
