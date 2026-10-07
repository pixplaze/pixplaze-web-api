package com.pixplaze.api.web.data.user;

import com.pixplaze.api.ext.data.auth.Authority;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовый принципал субъекта, авторизованного со стороны Minecraft
 * (source = {@link Authority.Source#MINECRAFT_AUTHORIZED_DEVICE MAD}). Роли ⊆
 * {@code {MINECRAFT_PLAYER, MINECRAFT_OPERATOR, MINECRAFT_SERVER}}, минимум одна.
 *
 * <p>Конкретная идентичность и форма токена — в наследниках:
 * {@link MinecraftPlayerPrincipal} (игрок/оператор; токен адресован серверу) и
 * {@link MinecraftServerPrincipal} (сервер; токен верифицируется снаружи по публичному ключу).
 * Такой принципал имеет ограниченный доступ к API приложения.
 *
 * <p>{@code serverId} — id MC-сервера ({@code minecraft_server.id}), общий для обоих наследников:
 * из него строится аудитория ({@code aud}) токена. У сервера это его собственный id, у игрока —
 * id сервера, против которого он авторизован.
 */
@Getter
@Setter
public abstract class MinecraftClientPrincipal extends ClientPrincipal {
    private Long minecraftServerId;
}
