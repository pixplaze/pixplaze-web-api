package com.pixplaze.api.web.service.server.model;

import com.pixplaze.api.ext.data.server.MinecraftServerPortsInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.server.IntegrationType;
import com.pixplaze.api.web.data.server.MinecraftServerSnapshot;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;

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
 * @param rating      агрегат оценок из БД (среднее + число голосов); персистентен, не зависит от online
 */
public record MinecraftServerListingInfo(
        MinecraftServer base,
        MinecraftServerPortsInfo ports,
        IntegrationType integration,
        MinecraftServerSnapshot.Online online,
        MinecraftServerSnapshot.Plugin plugin,
        ServerRatingAggregate rating
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

    public MinecraftServerListingInfo withOnline(MinecraftServerSnapshot.Online newOnline) {
        return new MinecraftServerListingInfo(base, ports, integration, newOnline, plugin, rating);
    }

    public MinecraftServerListingInfo withPlugin(MinecraftServerSnapshot.Plugin newPlugin) {
        return new MinecraftServerListingInfo(base, ports, integration, online, newPlugin, rating);
    }

    public MinecraftServerListingInfo withRating(ServerRatingAggregate newRating) {
        return new MinecraftServerListingInfo(base, ports, integration, online, plugin, newRating);
    }
}
