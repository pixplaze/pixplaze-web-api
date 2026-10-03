package com.pixplaze.api.web.service.server;

import com.pixplaze.api.ext.data.player.MinecraftPlayerListInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerCoreInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.server.MinecraftServerSnapshot;
import com.pixplaze.api.web.service.server.model.MinecraftServerListingInfo;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Сведение тиров {@link MinecraftServerListingInfo} (base + online + plugin) в публичный DTO
 * {@link MinecraftServerInfo} с graceful-деградацией:
 * <ul>
 *   <li>online есть → {@code state = ONLINE}, live-MOTD/версия/игроки/ping/иконка;</li>
 *   <li>online нет → {@code state = OFFLINE}, только база владельца;</li>
 *   <li>plugin есть → добавляются tps/uptime/difficulty/plugins/metadata (Tier-3).</li>
 * </ul>
 * Чистая функция над снапшотом — без сетевого I/O, дёшево на request-пути.
 */
@Component
public class MinecraftServerListingAssembler {

    /// Плагин молчит дольше этого — Tier-3 считаем протухшим и скрываем (push с TTL).
    private static final Duration PLUGIN_TTL = Duration.ofMinutes(2);

    /** Полный DTO для листинга. {@code includeThumbnail=false} опускает тяжёлую иконку. */
    public MinecraftServerInfo toServerInfo(MinecraftServerListingInfo listing) {
        final var base = listing.base();
        final var online = listing.online();
        final var plugin = freshPlugin(listing);

        final var id = base.getId();
        final var name = base.getName();
        final var host = base.getHost();
        final var motd = online != null ? online.motd() : null;
        final var icon = online != null ? online.iconBase64() : null;
        final var core = online != null ? new MinecraftServerCoreInfo(online.core(), online.version()) : null;
        final var plugins = plugin != null ? plugin.plugins() : null;

        final var rating = listing.rating();
        final var ratingAverage = rating != null && rating.count() > 0 ? rating.average() : null;
        final var ratingCount = rating != null ? rating.count() : 0L;

        return MinecraftServerInfo.builder()
                .id(id)
                .name(name)
                .host(host)
                .motd(motd)
                .license(base.getIsLicense())
                .iconBase64(icon)
                .description(base.getDescription())
                .ports(listing.ports())
                .core(core)
                .state(toStateInfo(listing))
                .plugins(plugins)
                .rating(ratingAverage)
                .ratingCount(ratingCount)
                .build();
    }

    /** Только online-обновляемая часть (для `POST /servers/state`). */
    public MinecraftServerStateInfo toStateInfo(MinecraftServerListingInfo listing) {
        final var online = listing.online();
        final var plugin = freshPlugin(listing);

        final var status = online != null
                ? MinecraftServerStateInfo.Status.ONLINE
                : MinecraftServerStateInfo.Status.OFFLINE;
        final var players = online != null
                ? new MinecraftPlayerListInfo(online.playersMax(), online.playersOnline())
                : null;

        return MinecraftServerStateInfo.builder()
                .tps(plugin != null ? plugin.tps() : null)
                .ping(online != null ? online.pingMillis() : null)
                .uptime(plugin != null ? plugin.uptimeMillis() : null)
                .difficulty(plugin != null ? plugin.difficulty() : null)
                .status(status)
                .players(players)
                .build();
    }

    /// Возвращает plugin-снимок, только если он свежий (иначе {@code null} → Tier-3 скрыт).
    private MinecraftServerSnapshot.Plugin freshPlugin(MinecraftServerListingInfo listing) {
        final var plugin = listing.plugin();
        if (plugin == null || plugin.fetchedAt() == null) {
            return null;
        }
        return Instant.now().isBefore(plugin.fetchedAt().plus(PLUGIN_TTL)) ? plugin : null;
    }
}
