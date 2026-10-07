package com.pixplaze.api.web.service.server;

import com.pixplaze.api.ext.data.server.MinecraftServerCoreInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.service.server.model.MinecraftServerListing;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Сведение {@link MinecraftServerListing} в публичный DTO {@link MinecraftServerInfo}: описание —
 * из БД, состояние — слияние части пинга и свежей части плагина ({@link MinecraftServerStates#merge}),
 * забаненный сервер — BANNED. Чистая функция над снапшотом — без сетевого I/O, дёшево на request-пути.
 */
@Component
public class MinecraftServerListingAssembler {

    /// Плагин молчит дольше этого — его часть состояния считаем протухшей.
    private static final Duration PLUGIN_TTL = Duration.ofMinutes(2);

    public MinecraftServerInfo toServerInfo(MinecraftServerListing listing) {
        final var base = listing.base();
        final var rating = listing.rating();

        return MinecraftServerInfo.builder()
                .id(base.getId())
                .name(base.getName())
                .motd(base.getMotd())
                .isLicense(base.getIsLicense())
                .iconBase64(base.getIcon())
                .description(base.getDescription())
                .hosts(listing.hosts().hosts())
                .core(base.getCoreName() != null || base.getCoreVersion() != null
                        ? new MinecraftServerCoreInfo(base.getCoreName(), base.getCoreVersion())
                        : null)
                .state(toStateInfo(listing))
                .rating(rating != null && rating.count() > 0 ? rating.average() : null)
                .ratingCount(rating != null ? rating.count() : 0L)
                .build();
    }

    /** Состояние для API (`POST /servers/state`, листинг): наблюдаемое плюс BANNED у забаненного. */
    public MinecraftServerStateInfo toStateInfo(MinecraftServerListing listing) {
        final var observed = toObservedState(listing);
        return listing.base().getBannedAt() == null
                ? observed
                : MinecraftServerStateInfo.builder(observed).status(MinecraftServerStateInfo.Status.BANNED).build();
    }

    /** Наблюдаемое состояние (слияние частей, только ONLINE/OFFLINE) — то, что пишется в БД. */
    public MinecraftServerStateInfo toObservedState(MinecraftServerListing listing) {
        final var merged = MinecraftServerStates.merge(
                listing.ping() != null ? listing.ping().state() : null,
                isPluginFresh(listing) ? listing.plugin().state() : null,
                listing.integration()
        );
        return MinecraftServerStateInfo.builder(merged).minecraftServerId(listing.id()).build();
    }

    private static boolean isPluginFresh(MinecraftServerListing listing) {
        return listing.plugin() != null && Instant.now().isBefore(listing.plugin().at().plus(PLUGIN_TTL));
    }
}
