package com.pixplaze.api.web.service.presence;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Онлайн игроков по снимкам из heartbeat плагинов: кто и на каком сервере сейчас. Хранятся все игроки,
 * привязанные к профилю и нет. Снимок сервера живёт, пока идут heartbeat: плагин замолчал дольше TTL
 * части плагина — его игроки считаются не в сети.
 *
 * <p>Абстракция под замену in-memory реализации на распределённую (Redis) при нескольких экземплярах.
 */
public interface MinecraftPresenceStore {

    /// Сверяет хеш снимка сервера с сохранённым и при совпадении продлевает срок снимка.
    /// {@code false} — снимка нет, он истёк или хеш другой: плагин должен прислать список.
    boolean touch(long serverId, String playersHash);

    /// Заменяет снимок сервера целиком и возвращает изменения относительно прежнего.
    PresenceChanges replace(long serverId, String playersHash, List<MinecraftPlayerInfo> players);

    /// Где игрок сейчас; пусто — не в сети (или его сервер давно молчит).
    Optional<PlayerPresence> find(UUID playerUuid);

    /// Убирает истёкшие снимки; возвращает игроков, ушедших вместе с ними.
    Set<UUID> evictExpired();

    /// Игрок в сети: сервер и данные из снимка ({@link MinecraftPlayerInfo#presence}).
    record PlayerPresence(long minecraftServerId, MinecraftPlayerInfo player) {
    }

    /**
     * Изменения снимка сервера.
     *
     * @param changed новые игроки и те, у кого изменились ник, оператор или флаг «скрыт»
     * @param left    игроки, которых больше нет в снимке
     */
    record PresenceChanges(List<MinecraftPlayerInfo> changed, Set<UUID> left) {
    }
}
