package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.web.data.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class AccessTokenService extends UserJwtService<User> {

    public AccessTokenService(
            @Value("${app.token.access.signing.key}") String secret,
            @Value("${app.token.access.expiration}") int expirationInMinutes
    ) {
        super(secret, Duration.ofMinutes(expirationInMinutes));
    }

    @Override
    public Map<String, Object> buildClaims(User user) {
        return new HashMap<>() {{
            put(UserClaims.ID, user.getId());
            put(UserClaims.EMAIL, user.getEmail());
            put(UserClaims.ROLE, user.getRole());
        }};
    }
}
