package com.pixplaze.api.web.data.server;

import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Адреса сервера вместе с моментом последней записи: по нему снапшот не откатывает адреса
 * к прочитанным раньше.
 *
 * @param updatedAt максимальный {@code updated_at} среди адресов; {@code null} — адресов нет
 */
public record ServerHosts(List<MinecraftServerHostInfo> hosts, OffsetDateTime updatedAt) {

    public static final ServerHosts EMPTY = new ServerHosts(List.of(), null);

    /// Игровой адрес (HOST), по которому сервер пингуется и к которому подключаются игроки.
    public Optional<MinecraftServerHostInfo> gameHost() {
        return hosts.stream()
                .filter(host -> host.type() == MinecraftServerHostInfo.Type.HOST)
                .findFirst();
    }

    /// Свежее ли {@code other}, чем эти адреса: пустая отметка считается самой старой.
    public boolean isOlderThan(ServerHosts other) {
        return updatedAt == null || (other.updatedAt() != null && other.updatedAt().isAfter(updatedAt));
    }
}
