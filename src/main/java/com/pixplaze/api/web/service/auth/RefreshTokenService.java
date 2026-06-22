package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.web.data.user.ClientPrincipial;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class RefreshTokenService extends UserJwtService<ClientPrincipial> {
    @Getter
    private final Duration expirationTerm;

    public RefreshTokenService(
            @Value("${app.security.auth.token.refresh.signing.key}") String secret,
            @Value("${app.security.auth.token.refresh.expiration.days}") int expirationInDays
    ) {
        super(secret, Duration.ofDays(expirationInDays));
        this.expirationTerm = Duration.ofDays(expirationInDays);
    }

    @Override
    public String buildId() {
        return UUID.randomUUID().toString();
    }
}
