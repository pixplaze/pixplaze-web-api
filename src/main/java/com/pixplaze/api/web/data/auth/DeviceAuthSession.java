package com.pixplaze.api.web.data.auth;

import com.pixplaze.api.ext.data.auth.DeviceAuthenticationStateInfo;
import com.pixplaze.api.web.data.user.Profile;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class DeviceAuthSession {
    // Геттеры и сеттеры
    @Getter
    private final String userCode;
    @Getter
    private final String clientId;
    private final Instant expiresAt;
    private final AtomicInteger attemptsCount;
    @Getter
    @Setter
    private DeviceAuthenticationStateInfo<Profile> state;

    public DeviceAuthSession(String userCode, String clientId, Duration expiration) {
        this.userCode = userCode;
        this.clientId = clientId;
        this.expiresAt = createExpirationTime(expiration);
        this.attemptsCount = createAttemptsCount(expiration);
        this.state = new DeviceAuthenticationStateInfo<>();
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
