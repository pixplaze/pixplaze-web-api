package com.pixplaze.api.web.service.server;

import com.pixplaze.api.web.data.server.MinecraftServerSnapshot;
import com.pixplaze.api.web.data.server.MinecraftServerListingInfo;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реализация {@link MinecraftServerSnapshotStore}: живое состояние в {@link ConcurrentHashMap}
 * (пишут фоновые задачи), а читатели получают {@code volatile} неизменяемый список — lock-free
 * чтение, развязанное от частоты записей ({@link #publish} вызывается раз в цикл рефреша).
 *
 * <p>Стартовая реализация под один инстанс; интерфейс позволяет заменить на Redis без правки
 * вызывающего кода.
 */
@Component
public class InMemoryMinecraftServerSnapshotStore implements MinecraftServerSnapshotStore {

    private final Map<Long, MinecraftServerListingInfo> byId = new ConcurrentHashMap<>();
    private volatile List<MinecraftServerListingInfo> published = List.of();

    @Override
    public Optional<MinecraftServerListingInfo> find(long serverId) {
        return Optional.ofNullable(byId.get(serverId));
    }

    @Override
    public List<MinecraftServerListingInfo> findAll() {
        return published;
    }

    @Override
    public void replaceAll(Collection<MinecraftServerListingInfo> bases) {
        final var keep = new HashSet<Long>(bases.size());
        for (final var base : bases) {
            keep.add(base.id());
            // Сохраняем уже собранные online/plugin существующей записи, обновляя базу/интеграцию/рейтинг из БД.
            byId.merge(base.id(), base, (existing, incoming) ->
                    new MinecraftServerListingInfo(incoming.base(), incoming.ports(), incoming.integration(), existing.online(), existing.plugin(), incoming.rating()));
        }
        byId.keySet().removeIf(id -> !keep.contains(id));
    }

    @Override
    public void putOnline(long serverId, MinecraftServerSnapshot.Online online) {
        byId.computeIfPresent(serverId, (id, current) -> current.withOnline(online));
    }

    @Override
    public void putPlugin(long serverId, MinecraftServerSnapshot.Plugin plugin) {
        byId.computeIfPresent(serverId, (id, current) -> current.withPlugin(plugin));
    }

    @Override
    public void putRating(long serverId, ServerRatingAggregate rating) {
        byId.computeIfPresent(serverId, (id, current) -> current.withRating(rating));
    }

    @Override
    public void publish() {
        // Стабильный порядок по id → устойчивая пагинация и O(1) чтение.
        published = byId.values().stream()
                .sorted(Comparator.comparingLong(MinecraftServerListingInfo::id))
                .toList();
    }
}
