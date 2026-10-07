package com.pixplaze.api.web.service.server;

import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.server.ObservedState;
import com.pixplaze.api.web.data.server.ServerHosts;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import com.pixplaze.api.web.service.server.model.MinecraftServerListing;
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

    private final Map<Long, MinecraftServerListing> byId = new ConcurrentHashMap<>();
    private volatile List<MinecraftServerListing> published = List.of();

    @Override
    public Optional<MinecraftServerListing> find(long serverId) {
        return Optional.ofNullable(byId.get(serverId));
    }

    @Override
    public List<MinecraftServerListing> findAll() {
        return published;
    }

    @Override
    public void replaceAll(Collection<MinecraftServerListing> bases) {
        final var keep = new HashSet<Long>(bases.size());
        for (final var base : bases) {
            keep.add(base.id());
            byId.merge(base.id(), base, MinecraftServerListing::syncedWith);
        }
        byId.keySet().removeIf(id -> !keep.contains(id));
    }

    @Override
    public void putServer(MinecraftServerListing listing) {
        byId.putIfAbsent(listing.id(), listing);
    }

    @Override
    public void putPing(long serverId, ObservedState ping) {
        byId.computeIfPresent(serverId, (id, current) -> current.withPing(ping));
    }

    @Override
    public void putPlugin(long serverId, ObservedState plugin) {
        byId.computeIfPresent(serverId, (id, current) -> current.withPlugin(plugin));
    }

    @Override
    public void putBase(MinecraftServer base) {
        byId.computeIfPresent(base.getId(), (id, current) ->
                base.getUpdatedAt().isBefore(current.base().getUpdatedAt()) ? current : current.withBase(base));
    }

    @Override
    public void putHosts(long serverId, ServerHosts hosts) {
        byId.computeIfPresent(serverId, (id, current) ->
                current.hosts().isOlderThan(hosts) ? current.withHosts(hosts) : current);
    }

    @Override
    public void putRating(long serverId, ServerRatingAggregate rating) {
        byId.computeIfPresent(serverId, (id, current) -> current.withRating(rating));
    }

    @Override
    public void publish() {
        // Стабильный порядок по id → устойчивая пагинация и O(1) чтение.
        published = byId.values().stream()
                .sorted(Comparator.comparingLong(MinecraftServerListing::id))
                .toList();
    }
}
