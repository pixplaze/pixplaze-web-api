package com.pixplaze.api.web.service.server;

import com.pixplaze.api.ext.data.server.MinecraftServerPortsInfo;
import com.pixplaze.api.web.data.server.*;
import com.pixplaze.api.web.service.MinecraftServerMonitoringService;
import com.pixplaze.api.web.service.MinecraftServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Фоновая сборка материализованного снапшота листинга (write-side): весь сетевой I/O — здесь,
 * вне request-пути. Три независимые каденции:
 * <ul>
 *   <li>{@link #syncBase()} — синхронизирует базовый набор из БД (нечасто);</li>
 *   <li>{@link #pingTick()} — «вежливый» Tier-2 пинг: {@link MinecraftServerPingThrottle} решает, кого можно
 *       пинговать сейчас, пинги идут async через общий {@code serverFetchExecutor} (легко на
 *       виртуальные потоки), результат кладётся в стор; тик не блокируется мёртвыми серверами;</li>
 *   <li>{@link #publish()} — публикует накопленное для читателей (O(n) раз в интервал).</li>
 * </ul>
 * Прогрев — через {@code initialDelay}, без блокирующего {@code @PostConstruct}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinecraftServerListingRefreshService {

    private final MinecraftServerService minecraftServerService;
    private final MinecraftServerMonitoringService monitoringService;
    private final MinecraftServerSnapshotStore store;
    private final MinecraftServerPingThrottle throttle;
    private final ExecutorService serverFetchExecutor;

    /// Кэш адресов для пинга; обновляется в syncBase, читается в pingTick (без DB-хита каждый тик).
    private volatile List<ServerPingTarget> pingTargets = List.of();

    @Scheduled(
            fixedDelayString = "${app.servers.sync.millis:300000}",
            initialDelayString = "${app.servers.sync.initial-millis:5000}"
    )
    public void syncBase() {
        try {
            final var targets = minecraftServerService.findPingTargets();
            final Map<Long, Integer> portById = targets.stream()
                    .collect(Collectors.toMap(ServerPingTarget::serverId, ServerPingTarget::port, (a, b) -> a));
            final var ratingById = minecraftServerService.ratingAggregatesByServer();

            final var listed = minecraftServerService.findAllListed().stream()
                    // Фаза 3: интеграция ещё не в БД → все NONE. Флаг PLUGIN появится в фазе 4.
                    .map(server -> new MinecraftServerListingInfo(
                            server,
                            new MinecraftServerPortsInfo(portById.getOrDefault(server.getId(), 25565)),
                            IntegrationType.NONE, null, null,
                            ratingById.getOrDefault(server.getId(), ServerRatingAggregate.EMPTY)))
                    .toList();
            store.replaceAll(listed);
            this.pingTargets = targets;
            store.publish();
            log.debug("Server base synced: {} listed servers", listed.size());
        } catch (Exception e) {
            log.warn("Server base sync failed: {}", e.toString());
        }
    }

    @Scheduled(
            fixedDelayString = "${app.servers.ping-tick.millis:5000}",
            initialDelayString = "${app.servers.ping-tick.initial-millis:10000}"
    )
    public void pingTick() {
        final var targets = throttle.selectDue(pingTargets);
        log.debug("Pinging {} servers...", targets.size());
        for (final var target : targets) {
            CompletableFuture.supplyAsync(() -> ping(target), serverFetchExecutor)
                    .whenComplete((online, error) -> {
                        // null (недоступен/ошибка) ⇒ помечаем OFFLINE; иначе кладём Tier-2.
                        store.putOnline(target.serverId(), online);
                        if (online != null) {
                            throttle.recordSuccess(target);
                            log.trace("Pinging {}:{} completed...", target.host(), target.port());
                        } else {
                            throttle.recordFailure(target);
                            log.error("Pinging {}:{} failed, reason: {}...", target.host(), target.port(), error.getMessage());
                        }
                    });
        }
    }

    @Scheduled(fixedDelayString = "${app.servers.publish.millis:5000}")
    public void publish() {
        store.publish();
    }

    private MinecraftServerSnapshot.Online ping(ServerPingTarget target) {
        try {
            return monitoringService.pingOnline(target.host(), target.port());
        } catch (Exception e) {
            log.debug("Ping failed {}:{} — {}", target.host(), target.port(), e.toString());
            return null;
        }
    }
}
