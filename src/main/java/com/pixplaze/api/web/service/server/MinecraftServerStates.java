package com.pixplaze.api.web.service.server;

import com.pixplaze.api.ext.data.player.MinecraftPlayerListInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;

/**
 * Внутренние представления состояния сервера в web-api: части от пинга и слияние частей. Представления,
 * которыми обмениваются стороны (heartbeat плагина), живут в ext-api на {@link MinecraftServerStateInfo}.
 */
public final class MinecraftServerStates {

    private MinecraftServerStates() {
    }

    /// Часть пинга: сервер ответил по протоколу Minecraft.
    public static MinecraftServerStateInfo ping(Long pingMillis, Integer playersOnline, Integer playersMax) {
        return MinecraftServerStateInfo.builder()
                .ping(pingMillis)
                .status(MinecraftServerStateInfo.Status.ONLINE)
                .players(new MinecraftPlayerListInfo(playersMax, playersOnline))
                .build();
    }

    /// Часть пинга: сервер не ответил.
    public static MinecraftServerStateInfo offline() {
        return MinecraftServerStateInfo.builder()
                .status(MinecraftServerStateInfo.Status.OFFLINE)
                .build();
    }

    /**
     * Слияние частей. Свежая часть плагина побеждает в пересекающихся полях ({@code ping} — медианный пинг
     * игроков, {@code players}, {@code status}) и одна даёт tps/uptime/difficulty; без неё всё — из пинга.
     *
     * @param ping        часть пинга; {@code null} — сервер ещё не пинговали
     * @param plugin      свежая часть плагина; {@code null} — плагина нет или он молчит
     * @param integration интеграция сервера, если плагин молчит
     */
    public static MinecraftServerStateInfo merge(
            MinecraftServerStateInfo ping,
            MinecraftServerStateInfo plugin,
            MinecraftServerStateInfo.IntegrationStatus integration
    ) {
        final var base = ping != null ? ping : offline();
        if (plugin == null) {
            return MinecraftServerStateInfo.builder(base)
                    .tps(null)
                    .uptime(null)
                    .difficulty(null)
                    .integrationStatus(integration)
                    .build();
        }

        return MinecraftServerStateInfo.builder(base)
                .ping(plugin.ping() != null ? plugin.ping() : base.ping())
                .players(plugin.players() != null ? plugin.players() : base.players())
                .status(plugin.status() != null ? plugin.status() : base.status())
                .tps(plugin.tps())
                .uptime(plugin.uptime())
                .difficulty(plugin.difficulty())
                .integrationStatus(MinecraftServerStateInfo.IntegrationStatus.PLUGIN)
                .build();
    }
}
