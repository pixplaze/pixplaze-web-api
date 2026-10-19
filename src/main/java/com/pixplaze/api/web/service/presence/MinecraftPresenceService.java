package com.pixplaze.api.web.service.presence;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerHeartbeatInfo;
import com.pixplaze.api.web.repository.MinecraftPlayerRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Снимок игроков из heartbeat (п. 32 схемы). Плагин шлёт хеш снимка каждый раз, а список — только когда
 * хеш изменился или web-api его попросил ({@code isPlayersRequired}). Снимок всегда полный, поэтому любая
 * рассинхронизация чинится следующим heartbeat.
 *
 * <p>Онлайн живёт в {@link MinecraftPresenceStore}. В БД уходят только редкие изменения: флаг оператора
 * участников сервера и ник известных игроков (когда игрок в снимке новый или изменился) и время выхода
 * привязанных игроков ({@code last_seen_at}: пропал из снимка или замолчал его сервер).
 */
@Service
@RequiredArgsConstructor
public class MinecraftPresenceService {

    private final MinecraftPresenceStore minecraftPresenceStore;
    private final MinecraftServerRepository minecraftServerRepository;
    private final MinecraftPlayerRepository minecraftPlayerRepository;

    /// Принимает часть heartbeat со снимком игроков. Возвращает {@code isPlayersRequired}: {@code true} —
    /// у web-api нет снимка сервера или хеш другой, плагин должен сразу повторить heartbeat со списком.
    public boolean accept(long serverId, MinecraftServerHeartbeatInfo heartbeat) {
        if (heartbeat.players() == null) {
            return !minecraftPresenceStore.touch(serverId, heartbeat.playersHash());
        }

        final var changes = minecraftPresenceStore.replace(serverId, heartbeat.playersHash(), heartbeat.players());
        persist(serverId, changes.changed());
        minecraftPlayerRepository.markLastSeen(changes.left());
        return false;
    }

    /// Снимки замолчавших серверов: их игроки больше не в сети.
    @Scheduled(fixedDelayString = "PT1M")
    public void evictExpired() {
        minecraftPlayerRepository.markLastSeen(minecraftPresenceStore.evictExpired());
    }

    private void persist(long serverId, List<MinecraftPlayerInfo> changed) {
        for (var player : changed) {
            minecraftServerRepository.updatePlayerOperator(serverId, player.uuid(), player.isOperator());
            minecraftPlayerRepository.updateUsername(player.uuid(), player.username());
        }
    }
}
