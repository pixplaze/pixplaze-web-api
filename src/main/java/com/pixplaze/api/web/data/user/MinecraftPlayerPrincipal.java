package com.pixplaze.api.web.data.user;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Принципал Minecraft-игрока. Identity — {@code playerUuid}. {@code profileId} заполняется,
 * когда игрок связан с профилем приложения (src=MAD); для «чистого» игрока — {@code null}.
 * Роль всегда {@code MINECRAFT_PLAYER}: операторские права живут в игре и в токене доступа к плагину.
 * Токен игрока адресован шлюзу (плагин ходит с ним в web-api от имени игрока); сервер, через который
 * игрок вошёл, — {@code serverId} из {@link MinecraftClientPrincipal}.
 */
@Getter
@Setter
@NoArgsConstructor
public class MinecraftPlayerPrincipal extends MinecraftClientPrincipal {
    private UUID uuid;
    private String username;
    private Long profileId; // TODO: убедиться, что он действительно нужен, нет - убрать

    @Override
    public @Nonnull String getName() {
        return username;
    }
}
