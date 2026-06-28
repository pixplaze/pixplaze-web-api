package com.pixplaze.api.web.data.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.service.auth.device.DeviceAuthorizationStrategy;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DeviceAuthorizationSession<A> {
    @Getter
    @Setter
    private DeviceAuthorizationState<A> state;
    private final @Getter DeviceAuthorizationStrategy<A, ?> strategy;
    private final AtomicReference<Instant> expiresAt;
    private final AtomicInteger attemptsCount;
    private final AtomicReference<Instant> lastPolledAt = new AtomicReference<>();

    public DeviceAuthorizationSession(String clientId, String userCode, String deviceHashCode, DeviceAuthorizationStrategy<A, ?> strategy, Authority authority, A authorizationDetails, Duration expiration, Duration interval) {
        this.strategy = strategy;
        this.state = DeviceAuthorizationState.pending(clientId, userCode, deviceHashCode, authority, authorizationDetails);
        this.expiresAt = new AtomicReference<>(createExpirationTime(expiration));
        this.attemptsCount = createAttemptsCount(expiration, interval);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt.get());
    }

    /**
     * Урезает срок жизни сессии до не более чем {@code max} от текущего момента (никогда не
     * продлевает). Вызывается при grant, чтобы одобренная сессия (а с ней — bearer-QR обратного
     * логина) гасла в узком окне независимо от исходного TTL. Атомарно (CAS).
     */
    public void capExpiry(Duration max) {
        final var candidate = Instant.now().plus(max);
        expiresAt.updateAndGet(current -> candidate.isBefore(current) ? candidate : current);
    }

    /**
     * Списывает одну попытку опроса из бюджета сессии и сообщает, исчерпан ли бюджет
     * (попытка сверх лимита) — тогда сессию следует закрыть. Метод изменяет состояние:
     * каждый вызов = одна потраченная попытка, поэтому вызывается ровно раз на опрос.
     */
    public boolean consumeAttempt() {
        return attemptsCount.decrementAndGet() < 0;
    }

    /**
     * RFC 8628 §3.5: фиксирует факт опроса и сообщает, пришёл ли он раньше допустимого
     * интервала с момента последней принятой попытки. Слишком ранний опрос окно не
     * сдвигает — клиент должен выждать интервал от прошлой принятой попытки. Атомарно
     * (CAS), чтобы конкурентные опросы не проскочили проверку.
     */
    public boolean isPolledTooSoon(Duration interval) {
        final var now = Instant.now();
        while (true) {
            final var previous = lastPolledAt.get();
            if (previous != null && now.isBefore(previous.plus(interval))) {
                return true;
            }
            if (lastPolledAt.compareAndSet(previous, now)) {
                return false;
            }
        }
    }

    private static Instant createExpirationTime(Duration expiration) {
        return Instant.now().plusSeconds(expiration.toSeconds());
    }

    /// Бюджет опросов на сессию = сколько раз клиент успеет опросить за время жизни
    /// сессии, соблюдая интервал ({@code expiration / interval}). То есть при честном
    /// соблюдении интервала бюджета ровно хватает до истечения сессии; превышение
    /// (опрос чаще интервала) исчерпывает его раньше и закрывает сессию.
    /// Минимум — одна попытка (страховка от мисконфигурации interval ≥ expiration или 0).
    private static AtomicInteger createAttemptsCount(Duration expiration, Duration interval) {
        final var intervalSeconds = Math.max(1L, interval.toSeconds());
        final var budget = (int) (expiration.toSeconds() / intervalSeconds);
        return new AtomicInteger(Math.max(1, budget));
    }
}
