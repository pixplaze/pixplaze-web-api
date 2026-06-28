package com.pixplaze.api.web.data.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Принципал Minecraft-сервера. Identity — {@code serverId} ({@code minecraft_server.id});
 * выпускается device-flow при регистрации/повторной авторизации сервера (src=MAD).
 * Серверный токен адресован хосту сервера ({@code host} из {@link MinecraftClientPrincipal})
 * и верифицируется снаружи по публичному ключу. {@code getName()} — Lombok из {@code name}.
 */
@Getter
@Setter
@NoArgsConstructor
public class MinecraftServerPrincipal extends MinecraftClientPrincipal {
    private Long serverId;
    private String name;
}
