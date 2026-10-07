package com.pixplaze.api.web.service.server.model;

import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.server.ObservedState;
import com.pixplaze.api.web.data.server.ServerHosts;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;

import java.util.Optional;

/**
 * Материализованная запись листинга: описание из БД, адреса и две части состояния — от пинга и от
 * heartbeat плагина, каждая со своим временем. Иммутабельна — обновления делаются copy-with, чтобы
 * читатели снапшота видели согласованное состояние без блокировок.
 *
 * @param base        описание из {@code minecraft_server} (id, имя, motd, иконка, ядро, лицензия, бан, {@code updated_at})
 * @param hosts       адреса сервера из {@code minecraft_server_host} с моментом последней записи
 * @param integration интеграция из {@code minecraft_server_state}: NATIVE (без плагина) или PLUGIN
 * @param ping        часть пинга; {@code null} — в этом запуске ещё не пинговали и прогреть было нечем
 * @param pinged      пинговали ли сервер в этом запуске (часть пинга не прогрета из БД) — для записи в историю
 * @param plugin      часть плагина; {@code null} — heartbeat не приходил (свежесть проверяет assembler)
 * @param rating      агрегат оценок из БД (среднее + число голосов)
 */
public record MinecraftServerListing(
        MinecraftServer base,
        ServerHosts hosts,
        MinecraftServerStateInfo.IntegrationStatus integration,
        ObservedState ping,
        boolean pinged,
        ObservedState plugin,
        ServerRatingAggregate rating
) {
    /// Новая запись из БД: состояние только прогретое (или его нет), heartbeat ещё не приходил.
    public static MinecraftServerListing of(
            MinecraftServer base,
            ServerHosts hosts,
            MinecraftServerStateInfo.IntegrationStatus integration,
            ObservedState warmPing,
            ServerRatingAggregate rating
    ) {
        return new MinecraftServerListing(base, hosts, integration, warmPing, false, null, rating);
    }

    public long id() {
        return base.getId();
    }

    /// Игровой адрес (HOST), по которому сервер пингуется и к которому подключаются игроки.
    public Optional<MinecraftServerHostInfo> gameHost() {
        return hosts.gameHost();
    }

    /// Ответил ли сервер на последний пинг.
    public boolean isPingOnline() {
        return ping != null && ping.state().status() == MinecraftServerStateInfo.Status.ONLINE;
    }

    public MinecraftServerListing withPing(ObservedState newPing) {
        return new MinecraftServerListing(base, hosts, integration, newPing, true, plugin, rating);
    }

    public MinecraftServerListing withPlugin(ObservedState newPlugin) {
        return new MinecraftServerListing(base, hosts, integration, ping, pinged, newPlugin, rating);
    }

    public MinecraftServerListing withBase(MinecraftServer newBase) {
        return new MinecraftServerListing(newBase, hosts, integration, ping, pinged, plugin, rating);
    }

    public MinecraftServerListing withHosts(ServerHosts newHosts) {
        return new MinecraftServerListing(base, newHosts, integration, ping, pinged, plugin, rating);
    }

    public MinecraftServerListing withRating(ServerRatingAggregate newRating) {
        return new MinecraftServerListing(base, hosts, integration, ping, pinged, plugin, newRating);
    }

    /// Обновление из БД (base-sync) поверх живой записи: собранные в памяти части состояния сохраняются,
    /// описание и адреса берутся из БД, только если они не старше уже известных (иначе — гонка с записью,
    /// прошедшей после чтения base-sync).
    public MinecraftServerListing syncedWith(MinecraftServerListing fromDb) {
        final var newerBase = !fromDb.base().getUpdatedAt().isBefore(base.getUpdatedAt()) ? fromDb.base() : base;
        final var newerHosts = hosts.isOlderThan(fromDb.hosts()) ? fromDb.hosts() : hosts;
        return new MinecraftServerListing(newerBase, newerHosts, fromDb.integration(), ping, pinged, plugin, fromDb.rating());
    }
}
