package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.configuration.properties.AccessTokenProperties;
import com.pixplaze.api.web.data.user.ClientPrincipal;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/** ES256 access-токены профиля (identity = {@code pid}/{@code email}; aud = targets профиля). */
@Service
public class ProfileAccessTokenService extends AbstractEsClientTokenService<ApplicationClientPrincipal> {

    public ProfileAccessTokenService(
            AccessTokenKeys keys,
            AccessTokenProperties properties
    ) {
        super(keys, Duration.ofMinutes(properties.expirationMinutes()));
    }

    @Override
    protected void writeIdentityClaims(ApplicationClientPrincipal identity, Map<String, Object> claims) {
        if (identity.getId() != null) {
            claims.put(AccessTokenClaims.PID, identity.getId());
        }
        if (identity.getEmail() != null) {
            claims.put(AccessTokenClaims.EMAIL, identity.getEmail());
        }
    }

    /// claims → ApplicationClientPrincipal (вызывается ClientPrincipalReader по ролям).
    static ClientPrincipal toPrincipal(Claims claims, Authority authority) {
        final var profile = new ApplicationClientPrincipal();
        profile.setId(claims.get(AccessTokenClaims.PID, Long.class));
        profile.setName(claims.getSubject());
        profile.setEmail(claims.get(AccessTokenClaims.EMAIL, String.class));
        profile.setAuthority(authority);
        return profile;
    }
}
