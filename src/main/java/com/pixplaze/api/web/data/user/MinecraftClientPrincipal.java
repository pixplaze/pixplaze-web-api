package com.pixplaze.api.web.data.user;

import com.pixplaze.api.ext.data.auth.Authority;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовый принципал субъекта, авторизованного со стороны Minecraft
 * (source = {@link Authority.Source#MINECRAFT_AUTHORIZED_DEVICE MAD}). Роли ⊆
 * {@code {MINECRAFT_PLAYER, MINECRAFT_SERVER}}, ровно одна.
 *
 * <p>Конкретная идентичность и форма токена — в наследниках:
 * {@link MinecraftPlayerPrincipal} (игрок; токен адресован шлюзу) и
 * {@link MinecraftServerPrincipal} (сервер; токен верифицируется снаружи по публичному ключу).
 * Такой принципал имеет ограниченный доступ к API приложения.
 *
 * <p>{@code serverId} — id MC-сервера ({@code minecraft_server.id}), общий для обоих наследников:
 * он едет в claim {@code mc.sid}. У сервера это его собственный id (он же в {@code aud}), у игрока —
 * id сервера, через который он вошёл.
 */
@Getter
@Setter
public abstract class MinecraftClientPrincipal extends ClientPrincipal {
    private Long minecraftServerId;
}
