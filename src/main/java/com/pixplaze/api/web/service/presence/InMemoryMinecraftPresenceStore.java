package com.pixplaze.api.web.service.presence;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.web.service.server.MinecraftServerListingAssembler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link MinecraftPresenceStore} одного экземпляра web-api: снимок на сервер и индекс
 * «игрок → сервер». Снимок сервера меняется атомарно ({@link ConcurrentHashMap#compute}).
 */
@Component
public class InMemoryMinecraftPresenceStore implements MinecraftPresenceStore {

    /// Срок снимка без heartbeat — общий с частью плагина в листинге.
    private static final Duration TTL = MinecraftServerListingAssembler.PLUGIN_TTL;

    private final Map<Long, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerServers = new ConcurrentHashMap<>();

    @Override
    public boolean touch(long serverId, String playersHash) {
        final var now = Instant.now();
        final var matched = new boolean[1];
        snapshots.computeIfPresent(serverId, (id, snapshot) -> {
            if (snapshot.isFresh(now) && Objects.equals(snapshot.hash(), playersHash)) {
                matched[0] = true;
                return snapshot.at(now);
            }
            return snapshot;
        });
        return matched[0];
    }

    @Override
    public PresenceChanges replace(long serverId, String playersHash, List<MinecraftPlayerInfo> players) {
        final var next = new LinkedHashMap<UUID, MinecraftPlayerInfo>();
        players.stream().filter(player -> player.uuid() != null).forEach(player -> next.put(player.uuid(), player));

        final var changed = new ArrayList<MinecraftPlayerInfo>();
        final var left = new HashSet<UUID>();
        snapshots.compute(serverId, (id, previous) -> {
            final Map<UUID, MinecraftPlayerInfo> before = previous == null ? Map.of() : previous.players();
            next.values().stream().filter(player -> !player.equals(before.get(player.uuid()))).forEach(changed::add);
            before.keySet().stream().filter(uuid -> !next.containsKey(uuid)).forEach(left::add);
            return new Snapshot(playersHash, Map.copyOf(next), Instant.now());
        });

        left.forEach(uuid -> playerServers.remove(uuid, serverId));
        next.keySet().forEach(uuid -> playerServers.put(uuid, serverId));
        return new PresenceChanges(changed, left);
    }

    @Override
    public Optional<PlayerPresence> find(UUID playerUuid) {
        final var serverId = playerServers.get(playerUuid);
        if (serverId == null) {
            return Optional.empty();
        }
        final var snapshot = snapshots.get(serverId);
        if (snapshot == null || !snapshot.isFresh(Instant.now())) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.players().get(playerUuid))
                .map(player -> new PlayerPresence(serverId, player));
    }

    @Override
    public Set<UUID> evictExpired() {
        final var now = Instant.now();
        final var left = new HashSet<UUID>();
        snapshots.forEach((serverId, snapshot) -> {
            if (!snapshot.isFresh(now) && snapshots.remove(serverId, snapshot)) {
                snapshot.players().keySet().forEach(uuid -> {
                    if (playerServers.remove(uuid, serverId)) {
                        left.add(uuid);
                    }
                });
            }
        });
        return left;
    }

    private record Snapshot(String hash, Map<UUID, MinecraftPlayerInfo> players, Instant updatedAt) {

        boolean isFresh(Instant now) {
            return now.isBefore(updatedAt.plus(TTL));
        }

        Snapshot at(Instant now) {
            return new Snapshot(hash, players, now);
        }
    }
}
