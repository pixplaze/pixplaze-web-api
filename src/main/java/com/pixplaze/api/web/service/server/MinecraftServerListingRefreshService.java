package com.pixplaze.api.web.service.server;

import com.pixplaze.api.ext.data.server.MinecraftServerPortsInfo;
import com.pixplaze.api.web.data.server.*;
import com.pixplaze.api.web.service.MinecraftServerMonitoringService;
import com.pixplaze.api.web.service.MinecraftServerService;
import com.pixplaze.api.web.service.server.model.MinecraftServerListingInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
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
                            IntegrationType.ONLINE, null, null,
                            ratingById.getOrDefault(server.getId(), ServerRatingAggregate.EMPTY)))
                    .toList();
            store.replaceAll(listed);
            pingTargets = targets;
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
        if (targets.isEmpty()) {
            return;
        }
        log.debug("Pinging {} servers...", targets.size());

        // Счётчик отказов пачки (atomic — инкремент на потоках executor'а). Успехи отдельно не считаем:
        // каждый пинг завершается ровно раз и попадает в одну ветку ⇒ ok == targets.size() - failed.
        final var failed = new AtomicInteger();

        final var batch = targets.stream()
                .map(target -> CompletableFuture
                        .supplyAsync(() -> ping(target), serverFetchExecutor)
                        // Логирование — на уровне CF: ping пробрасывает причину, ошибку видим здесь.
                        .whenComplete((online, error) -> {
                            store.putOnline(target.serverId(), online);
                            if (error == null) {
                                throttle.recordSuccess(target);
                                log.trace("Pinged {}:{} ok", target.host(), target.port());
                            } else {
                                throttle.recordFailure(target);
                                failed.incrementAndGet();
                                log.debug("Ping failed {}:{} — {}", target.host(), target.port(), rootMessage(error));
                            }
                        }))
                .toArray(CompletableFuture[]::new);

        // Сводка по завершении ВСЕЙ пачки (не блокирует тик — колбэк асинхронный).
        CompletableFuture.allOf(batch).whenComplete((ignored, ignoredError) -> {
            final var failedCount = failed.get();
            final var okCount = targets.size() - failedCount;
            if (failedCount > 0) {
                log.info("Ping batch: {}/{} failed ({} ok)", failedCount, targets.size(), okCount);
            } else {
                log.debug("Ping batch: all {} ok", okCount);
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.servers.publish.millis:5000}")
    public void publish() {
        store.publish();
    }

    /// Один пинг. Причину НЕ глушим — пробрасываем в CompletableFuture (checked IOException заворачиваем
    /// в CompletionException), чтобы логирование/учёт шли на уровне CF-цепочки в {@link #pingTick()}.
    private MinecraftServerSnapshot.Online ping(ServerPingTarget target) {
        try {
            return monitoringService.pingOnline(target.host(), target.port());
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    /// Корневая причина в компактном виде для лога: снимает обёртку CompletionException.
    private static String rootMessage(Throwable error) {
        final var cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() != null
                ? cause.getClass().getSimpleName() + ": " + cause.getMessage()
                : cause.getClass().getSimpleName();
    }
}
