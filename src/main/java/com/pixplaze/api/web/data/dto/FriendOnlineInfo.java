package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Онлайн взаимного друга (или самого профиля).
 *
 * @param players    игроки профиля в сети; у друга со «скрывать онлайн» — пусто, игрок в ванише не попадает
 * @param lastSeenAt когда игроки профиля последний раз были в сети; у друга со «скрывать онлайн» — {@code null}
 */
public record FriendOnlineInfo(Long profileId, String name, List<Player> players, OffsetDateTime lastSeenAt) {

    /// Игрок в сети и сервер, на котором он сейчас: {@link MinecraftServerInfo#brief} — id, название и адреса.
    /// Сервера нет в снапшоте (ещё не прогрет) — только id.
    public record Player(UUID uuid, String username, MinecraftServerInfo server) {
    }
}
