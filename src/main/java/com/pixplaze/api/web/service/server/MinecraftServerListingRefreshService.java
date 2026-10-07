package com.pixplaze.api.web.service.server;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerState;
import com.pixplaze.api.web.data.server.*;
import com.pixplaze.api.web.service.MinecraftServerMonitoringService;
import com.pixplaze.api.web.service.MinecraftServerService;
import com.pixplaze.api.web.service.server.model.MinecraftServerListing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final MinecraftServerStateRecorder stateRecorder;

    /// Сохранённое онлайн-состояние старше этого после рестарта не показываем: сервер мог давно упасть.
    @Value("${app.servers.warm-start.max-age:10m}")
    private Duration warmStartMaxAge;

    @Scheduled(
            fixedDelayString = "${app.servers.sync.millis:300000}",
            initialDelayString = "${app.servers.sync.initial-millis:5000}"
    )
    public void syncBase() {
        try {
            final var hostsById = minecraftServerService.findAllHosts();
            final var stateById = minecraftServerService.findAllStates();
            final var ratingById = minecraftServerService.ratingAggregatesByServer();
            final var now = Instant.now();

            final var listed = minecraftServerService.findAllListed().stream()
                    .map(server -> {
                        final var state = stateById.get(server.getId());
                        return MinecraftServerListing.of(
                                server,
                                hostsById.getOrDefault(server.getId(), ServerHosts.EMPTY),
                                state != null ? state.getIntegrationStatus() : MinecraftServerStateInfo.IntegrationStatus.NATIVE,
                                warmPing(state, now),
                                ratingById.getOrDefault(server.getId(), ServerRatingAggregate.EMPTY));
                    })
                    .toList();
            // Новые записи берут прогретую часть пинга из БД; у известных стор сохраняет собранное в памяти.
            store.replaceAll(listed);
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
        final var targets = throttle.selectDue(pingTargets());
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
                        .whenComplete((pinged, error) -> {
                            final var previous = store.find(target.minecraftServerId()).orElse(null);
                            final var part = pinged != null ? pinged.state() : MinecraftServerStates.offline();
                            store.putPing(target.minecraftServerId(), new ObservedState(
                                    MinecraftServerStateInfo.builder(part).minecraftServerId(target.minecraftServerId()).build(),
                                    Instant.now()));
                            if (pinged != null) {
                                minecraftServerService.updatePingDescription(target.minecraftServerId(), pinged);
                            }
                            // Первый пинг запуска или смена online/offline — внеочередной замер в БД.
                            if (previous != null && (!previous.pinged() || previous.isPingOnline() != (pinged != null))) {
                                stateRecorder.record(target.minecraftServerId());
                            }
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

    /// Цели пинга — игровые адреса из опубликованного вида стора (без DB-хита каждый тик);
    /// адреса, обновлённые {@link MinecraftServerSnapshotStore#putHosts}, попадают сюда с ближайшим publish.
    private List<ServerPingTarget> pingTargets() {
        return store.findAll().stream()
                .flatMap(listing -> listing.gameHost()
                        .map(host -> new ServerPingTarget(listing.id(), host.address(), host.port()))
                        .stream())
                .toList();
    }

    /// Прогрев после рестарта: последнее сохранённое ONLINE-состояние как часть пинга, если оно свежее
    /// {@code app.servers.warm-start.max-age}; иначе {@code null} — сервер считается offline до первого пинга.
    private ObservedState warmPing(MinecraftServerState state, Instant now) {
        if (state == null
                || state.getStatus() != MinecraftServerStateInfo.Status.ONLINE
                || state.getUpdatedAt() == null
                || state.getUpdatedAt().toInstant().isBefore(now.minus(warmStartMaxAge))) {
            return null;
        }

        final var ping = MinecraftServerStates.ping(state.getPing(), state.getPlayersOnline(), state.getPlayersMax());
        return new ObservedState(
                MinecraftServerStateInfo.builder(ping).minecraftServerId(state.getMinecraftServerId()).build(),
                state.getUpdatedAt().toInstant());
    }

    /// Один пинг: описание сервера и часть состояния. Причину НЕ глушим — пробрасываем в CompletableFuture
    /// (checked IOException заворачиваем в CompletionException), чтобы логирование/учёт шли на уровне CF-цепочки.
    private MinecraftServerInfo ping(ServerPingTarget target) {
        try {
            return monitoringService.ping(target.host(), target.port());
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
