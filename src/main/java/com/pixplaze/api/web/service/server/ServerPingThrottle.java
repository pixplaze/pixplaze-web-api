package com.pixplaze.api.web.service.server;

import com.pixplaze.api.web.data.server.ServerPingTarget;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * «Вежливая» политика pull-пинга (анти-скан): решает, какие серверы можно пинговать прямо сейчас,
 * и планирует следующий заход. Гарантии:
 * <ul>
 *   <li><b>Непредсказуемость:</b> у каждого сервера свой {@code nextCheckAt} = base ± jitter; порядок
 *       выбора тасуется — никаких синхронных «раз в N по всем».</li>
 *   <li><b>Троттлинг по инфраструктуре:</b> цели группируются по подсети (/24 по резолву host→IP);
 *       на подсеть — не более {@link #SUBNET_MAX_CONCURRENT} одновременного пинга и не чаще
 *       {@link #SUBNET_MIN_INTERVAL}. Серверы одного хостера не бомбятся пачкой.</li>
 *   <li><b>Адаптивный бэкофф:</b> недоступные — экспоненциально реже (до {@link #MAX_BACKOFF}).</li>
 *   <li><b>Глобальный потолок:</b> не более {@link #MAX_PINGS_PER_TICK} за тик.</li>
 * </ul>
 * Значения пока константы — при необходимости выносятся в конфиг.
 */
@Component
public class ServerPingThrottle {

    private static final Duration BASE_INTERVAL = Duration.ofMinutes(5);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);
    private static final Duration SUBNET_MIN_INTERVAL = Duration.ofSeconds(10);
    private static final int SUBNET_MAX_CONCURRENT = 1;
    private static final int MAX_PINGS_PER_TICK = 50;
    private static final double JITTER = 0.4; // ±40%
    private static final Duration DNS_TTL = Duration.ofMinutes(10);
    private static final int MAX_FAILURE_EXP = 6;

    private final Map<Long, Schedule> schedules = new ConcurrentHashMap<>();
    private final Map<String, SubnetGate> gates = new ConcurrentHashMap<>();
    private final Map<String, DnsEntry> dnsCache = new ConcurrentHashMap<>();

    /** Отбирает цели, которые можно пинговать сейчас; помечает их занятыми (claim). */
    public List<ServerPingTarget> selectDue(List<ServerPingTarget> targets) {
        final var now = Instant.now();

        final var due = new ArrayList<>(targets);
        due.removeIf(target -> now.isBefore(schedule(target.serverId()).nextCheckAt));
        Collections.shuffle(due); // непредсказуемый порядок

        final var picked = new ArrayList<ServerPingTarget>();
        for (final var target : due) {
            if (picked.size() >= MAX_PINGS_PER_TICK) {
                break;
            }
            final var gate = gate(subnetOf(target.host()));
            if (gate.inFlight.get() >= SUBNET_MAX_CONCURRENT) {
                continue;
            }
            if (gate.lastAttemptAt != null && now.isBefore(gate.lastAttemptAt.plus(SUBNET_MIN_INTERVAL))) {
                continue;
            }
            // claim: занимаем подсеть и тентативно сдвигаем nextCheckAt (финализируется в record*),
            // чтобы не выбрать сервер повторно до получения результата.
            gate.inFlight.incrementAndGet();
            gate.lastAttemptAt = now;
            schedule(target.serverId()).nextCheckAt = now.plus(BASE_INTERVAL);
            picked.add(target);
        }
        return picked;
    }

    public void recordSuccess(ServerPingTarget target) {
        final var schedule = schedule(target.serverId());
        schedule.failures = 0;
        schedule.nextCheckAt = Instant.now().plusMillis(jitter(BASE_INTERVAL.toMillis()));
        release(target);
    }

    public void recordFailure(ServerPingTarget target) {
        final var schedule = schedule(target.serverId());
        schedule.failures = Math.min(schedule.failures + 1, MAX_FAILURE_EXP);
        final var backoffMillis = Math.min(MAX_BACKOFF.toMillis(), BASE_INTERVAL.toMillis() * (1L << schedule.failures));
        schedule.nextCheckAt = Instant.now().plusMillis(jitter(backoffMillis));
        release(target);
    }

    private void release(ServerPingTarget target) {
        final var gate = gate(subnetOf(target.host()));
        gate.inFlight.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    private static long jitter(long millis) {
        final var factor = 1 + ThreadLocalRandom.current().nextDouble(-JITTER, JITTER);
        return (long) (millis * factor);
    }

    private Schedule schedule(long serverId) {
        return schedules.computeIfAbsent(serverId, id -> new Schedule());
    }

    private SubnetGate gate(String subnetKey) {
        return gates.computeIfAbsent(subnetKey, key -> new SubnetGate());
    }

    /// Ключ подсети (/24 для IPv4, полный адрес для IPv6) по кэшированному резолву host→IP.
    /// Нерезолвящийся host — собственная «подсеть», чтобы не смешивать с чужими.
    private String subnetOf(String host) {
        final var cached = dnsCache.get(host);
        if (cached != null && Instant.now().isBefore(cached.at().plus(DNS_TTL))) {
            return cached.key();
        }
        String key;
        try {
            final var inet = InetAddress.getByName(host);
            final var address = inet.getAddress();
            key = address.length == 4
                    ? (address[0] & 255) + "." + (address[1] & 255) + "." + (address[2] & 255) + ".0/24"
                    : inet.getHostAddress();
        } catch (Exception e) {
            key = "unresolved:" + host;
        }
        dnsCache.put(host, new DnsEntry(key, Instant.now()));
        return key;
    }

    private static final class Schedule {
        volatile Instant nextCheckAt = Instant.EPOCH; // новый сервер — сразу due
        volatile int failures = 0;
    }

    private static final class SubnetGate {
        final AtomicInteger inFlight = new AtomicInteger();
        volatile Instant lastAttemptAt;
    }

    private record DnsEntry(String key, Instant at) {}
}
