package com.pixplaze.api.web.data.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.service.auth.device.DeviceAuthorizationStrategy;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class DeviceAuthorizationSession<D> {
    @Getter
    @Setter
    private DeviceAuthorizationState<D> state;
    private final @Getter DeviceAuthorizationStrategy<D, ?> strategy;
    private final Instant expiresAt;
    private final AtomicInteger attemptsCount;

    public DeviceAuthorizationSession(String clientId, String userCode, String deviceHashCode, DeviceAuthorizationStrategy<D, ?> strategy, Authority authority, D authorizationDetails, Duration expiration) {
        this.strategy = strategy;
        this.state = DeviceAuthorizationState.pending(clientId, userCode, deviceHashCode, authority, authorizationDetails);
        this.expiresAt = createExpirationTime(expiration);
        this.attemptsCount = createAttemptsCount(expiration);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isExhausted() {
        final var attemptsRemain = attemptsCount.decrementAndGet();
        return attemptsRemain < 0;
    }

    private static Instant createExpirationTime(Duration expiration) {
        return Instant.now().plusSeconds(expiration.toSeconds());
    }

    /// Создаёт [AtomicInteger] равный количеству допустимых попыток за сессию.
    /// Количество попыток генерируется из расчёта, что допустимо не больше одной попытки за 5 секунд.
    private static AtomicInteger createAttemptsCount(Duration expiration) {
        return new AtomicInteger((int) (expiration.toSeconds() / 5));
    }
}
