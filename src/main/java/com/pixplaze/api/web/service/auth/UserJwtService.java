package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.web.data.user.Role;
import com.pixplaze.api.web.data.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

public class UserJwtService<E extends User> implements JwtService<E> {

    public static class UserClaims {
        public static final String ID = "id";
        public static final String EMAIL = "email";
        public static final String ROLE = "role";
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

    public User extractUser(String token) {
        return extractUser(extractClaims(token));
    }

    public User extractUser(Claims claims) {
        return new User(
                claims.get(UserClaims.ID, Long.class),
                claims.getSubject(),
                claims.get(UserClaims.EMAIL, String.class),
                null,
                Role.of(claims.get(UserClaims.ROLE, String.class))
        );
    }

    @Override
    public String generate(E user) {
        return Jwts.builder()
                .id(buildId())
                .subject(user.getUsername())
                .claims(buildClaims(user))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(expirationTerm)))
                .signWith(secretKey)
                .compact();
    }

    public Map<String, Object> buildClaims(User user) {
        return Map.of();
    }

    public String buildId() {
        return null;
    }
}
