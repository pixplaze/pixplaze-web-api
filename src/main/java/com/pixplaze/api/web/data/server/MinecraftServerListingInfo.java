package com.pixplaze.api.web.data.server;

import com.pixplaze.api.ext.data.server.MinecraftServerPortsInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Материализованная запись листинга: базовые данные из БД (+ порты) + опциональные тиры online
 * (Tier-2) и plugin (Tier-3). Иммутабельна — обновления делаются copy-with (`withOnline`/`withPlugin`),
 * чтобы читатели снапшота видели согласованное состояние без блокировок.
 *
 * @param base        авторитетная база из {@code minecraft_server} (id/host/name/motd/…)
 * @param ports       порты сервера (для подключения/отображения)
 * @param integration NONE (owner-без-плагина) или PLUGIN
 * @param online      Tier-2, {@code null} если сервер сейчас недоступен
 * @param plugin      Tier-3, {@code null} если плагина нет или он молчит
 */
public record MinecraftServerListingInfo(
        MinecraftServer base,
        MinecraftServerPortsInfo ports,
        IntegrationType integration,
        @Nullable OnlineSnapshot online,
        @Nullable PluginSnapshot plugin
) {
    public long id() {
        return base.getId();
    }

    public String host() {
        return base.getHost();
    }

    public boolean isOnline() {
        return online != null;
    }

    public MinecraftServerListingInfo withOnline(@Nullable OnlineSnapshot newOnline) {
        return new MinecraftServerListingInfo(base, ports, integration, newOnline, plugin);
    }

    public MinecraftServerListingInfo withPlugin(@Nullable PluginSnapshot newPlugin) {
        return new MinecraftServerListingInfo(base, ports, integration, online, newPlugin);
    }
}
