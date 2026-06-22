package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.user.ClientPrincipial;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

public class UserJwtService<E extends ClientPrincipial> implements JwtService<E> {

    public static class UserClaims {
        public static final String ID = "pid";
        public static final String EMAIL = "email";
        public static final String ROLE = "rid";
    }

    private final SecretKey secretKey;
    private final Duration expirationTerm;

    public UserJwtService(String secret, Duration expirationTerm) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationTerm = expirationTerm;
    }

    @Override
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public ClientPrincipial readClaims(String token) {
        return readClaims(extractClaims(token));
    }

    public ClientPrincipial readClaims(Claims claims) {
        return new ClientPrincipial(
                claims.get(UserClaims.ID, Long.class),
                claims.getSubject(),
                claims.get(UserClaims.EMAIL, String.class),
                null,
                Authority.as(Authority.Role.valueOf((String) claims.get(Authority.Claims.ROLE)))
                        .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
                        .to(Authority.Target.PIXPLAZE)
                        .grant()
        );
    }

    @Override
    public String generate(E principial) {
        return Jwts.builder()
                .id(buildId())
                .subject(principial.getUsername())
                .claims(buildClaims(principial))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(expirationTerm)))
                .signWith(secretKey)
                .compact();
    }

    public Map<String, Object> buildClaims(ClientPrincipial clientPrincipial) {
        return Map.of();
    }

    public String buildId() {
        return null;
    }
}
