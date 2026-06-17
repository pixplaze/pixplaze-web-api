package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.web.data.user.Profile;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RefreshTokenService extends UserJwtService<Profile> {
    @Getter
    private final Duration expirationTerm;

    public RefreshTokenService(
            @Value("${app.token.refresh.signing.key}") String secret,
            @Value("${app.token.refresh.expiration}") int expirationInDays
    ) {
        super(secret, Duration.ofDays(expirationInDays));
        this.expirationTerm = Duration.ofDays(expirationInDays);
    }

    @Override
    public String buildId() {
        return UUID.randomUUID().toString();
    }
}
