package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.user.ClientPrincipial;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class AccessTokenService extends UserJwtService<ClientPrincipial> {

    public AccessTokenService(
            @Value("${app.security.auth.token.access.signing.key}") String secret,
            @Value("${app.security.auth.token.access.expiration.minutes}") int expirationInMinutes
    ) {
        super(secret, Duration.ofMinutes(expirationInMinutes));
    }

    @Override
    public Map<String, Object> buildClaims(ClientPrincipial clientPrincipial) {
        return Map.of(
            UserClaims.ID, clientPrincipial.getId(),
            UserClaims.EMAIL, clientPrincipial.getEmail(),
            Authority.Claims.ROLE, clientPrincipial.getAuthority().role().name()
        );
    }
}
