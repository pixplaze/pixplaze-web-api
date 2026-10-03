package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.web.service.auth.device.model.ApproverDecision;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationRequest;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationState;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реализация {@link DeviceAuthorizationStore}.
 *
 * <p><b>Соответствие Redis.</b> {@link Entry} — аналог ключей одной сессии ({@code dfs}, {@code dfd},
 * {@code dfp}), а каждый её {@code synchronized}-метод — аналог одной транзакции {@code MULTI}.
 * Операция над записью видит и меняет её целиком, поэтому наблюдаемое поведение совпадает
 * с Redis-реализацией без оговорок про узкие окна. Индекс по user-коду живёт своим сроком
 * и при удалении записи не чистится — так же, как в Redis.
 *
 * <p><b>Удалённая запись мертва и для тех, кто успел её прочитать.</b> Захват и удаление не
 * только убирают запись из карты, но и помечают её: параллельная операция, взявшая ссылку
 * раньше, увидит «записи нет», как увидела бы отсутствующий ключ в Redis.
 *
 * <p><b>Истечение проверяется на каждом чтении.</b> {@link #evictExpired()} только освобождает
 * память и на видимость записей не влияет; вызывать его периодически — забота конфигурации.
 */
public class InMemoryDeviceAuthorizationStore implements DeviceAuthorizationStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, UserCodeIndex> userCodeIndex = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryDeviceAuthorizationStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryCreate(DeviceAuthorizationRequest request, @Nullable String details, int attemptBudget, Duration ttl) {
        final var expiresAt = clock.instant().plus(ttl);

        if (!tryReserveUserCode(request, expiresAt)) {
            return false;
        }

        entries.put(request.deviceCodeHash(), new Entry(request, details, attemptBudget, expiresAt));
        return true;
    }

    @Override
    public Optional<ConsumedAttempt> readAndConsumeAttempt(String deviceCodeHash, Duration interval) {
        final var entry = entries.get(deviceCodeHash);

        return entry == null
                ? Optional.empty()
                : entry.readAndConsumeAttempt(clock.instant(), interval);
    }

    @Override
    public Optional<DeviceAuthorizationState> findByUserCode(String userCode) {
        final var now = clock.instant();

        return Optional.ofNullable(userCodeIndex.get(userCode))
                .filter(index -> index.isLive(now))
                .map(index -> entries.get(index.deviceCodeHash()))
                .flatMap(entry -> entry.snapshot(now));
    }

    @Override
    public Optional<DecisionOutcome> putDecisionIfAbsent(String deviceCodeHash, ApproverDecision decision, Duration ttl) {
        final var entry = entries.get(deviceCodeHash);

        return entry == null
                ? Optional.empty()
                : entry.putDecisionIfAbsent(decision, clock.instant(), ttl);
    }

    @Override
        public Optional<DeviceAuthorizationState> takeAndRemove(String deviceCodeHash) {
        // ConcurrentHashMap.remove отдаёт запись ровно одному из параллельных вызовов.
        final var entry = entries.remove(deviceCodeHash);

        return entry == null ? Optional.empty() : entry.discard(clock.instant());
    }

    @Override
    public void remove(String deviceCodeHash) {
        takeAndRemove(deviceCodeHash);
    }

    /**
     * Освобождает память от истёкших записей и индексов.
     *
     * @return сколько записей удалено
     */
    public int evictExpired() {
        final var now = clock.instant();
        var evicted = 0;

        for (final var entry : entries.entrySet()) {
            // remove(key, value) не тронет запись, если ключ успели занять заново.
            if (!entry.getValue().isLive(now) && entries.remove(entry.getKey(), entry.getValue())) {
                evicted++;
            }
        }

        userCodeIndex.values().removeIf(index -> !index.isLive(now));

        return evicted;
    }

    /// Аналог {@code SET dfu:code h NX EX ttl}: код свободен, если индекса нет или он истёк.
    private boolean tryReserveUserCode(DeviceAuthorizationRequest request, Instant expiresAt) {
        final var now = clock.instant();
        final var candidate = new UserCodeIndex(request.deviceCodeHash(), expiresAt);
        final var reserved = userCodeIndex.merge(request.userCode(), candidate,
                (current, fresh) -> current.isLive(now) ? current : fresh);

        return reserved == candidate;
    }

    private record UserCodeIndex(String deviceCodeHash, Instant expiresAt) {

        boolean isLive(Instant now) {
            return now.isBefore(expiresAt);
        }
    }

    /// Все данные одной сессии. Каждый метод — одна атомарная операция, как {@code MULTI} в Redis.
    private static final class Entry {

        private final DeviceAuthorizationRequest request;
        private final @Nullable String details;

        private Instant expiresAt;
        private int attemptsLeft;
        private @Nullable ApproverDecision decision;
        private @Nullable Instant nextPollAllowedAt;
        private boolean discarded;

        private Entry(DeviceAuthorizationRequest request, @Nullable String details, int attemptBudget, Instant expiresAt) {
            this.request = request;
            this.details = details;
            this.attemptsLeft = attemptBudget;
            this.expiresAt = expiresAt;
        }

        synchronized boolean isLive(Instant now) {
            return !discarded && now.isBefore(expiresAt);
        }

        synchronized Optional<DeviceAuthorizationState> snapshot(Instant now) {
            return isLive(now) ? Optional.of(state()) : Optional.empty();
        }

        synchronized Optional<ConsumedAttempt> readAndConsumeAttempt(Instant now, Duration interval) {
            if (!isLive(now)) {
                return Optional.empty();
            }

            attemptsLeft--;

            final var tooSoon = nextPollAllowedAt != null && now.isBefore(nextPollAllowedAt);

            // Окно сдвигает только принятое обращение — аналог SET dfp NX PX interval.
            if (!tooSoon) {
                nextPollAllowedAt = now.plus(interval);
            }

            return Optional.of(new ConsumedAttempt(state(), tooSoon));
        }

        synchronized Optional<DecisionOutcome> putDecisionIfAbsent(ApproverDecision candidate, Instant now, Duration ttl) {
            if (!isLive(now)) {
                return Optional.empty();
            }

            // Срок сокращается независимо от исхода — как EXPIRE LT внутри MULTI.
            expiresAt = min(expiresAt, now.plus(ttl));

            final var stored = decision == null;

            if (stored) {
                decision = candidate;
            }

            return Optional.of(stored ? DecisionOutcome.DECIDED : DecisionOutcome.ALREADY_DECIDED);
        }

        /// Помечает запись удалённой и отдаёт снимок, если она была жива.
        synchronized Optional<DeviceAuthorizationState> discard(Instant now) {
            final var last = snapshot(now);
            discarded = true;

            return last;
        }

        private DeviceAuthorizationState state() {
            return new DeviceAuthorizationState(request, details, decision, attemptsLeft);
        }

        private static Instant min(Instant a, Instant b) {
            return a.isBefore(b) ? a : b;
        }
    }
}
