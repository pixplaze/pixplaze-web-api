package com.pixplaze.api.web.service;


import com.pixplaze.api.web.data.user.Role;
import com.pixplaze.api.web.data.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Deprecated(forRemoval = true)
public class JwtService0 {
    @Value("${app.token.access.signing.key}")
    private String accessSigningKey;
    @Value("${app.token.access.expiration}")
    private int accessExpiresInMinutes;
    @Value("${app.token.refresh.signing.key}")
    private String refreshSigningKey;
    @Value("${app.token.refresh.expiration}")
    private int refreshExpiresInDays;

    public static class ExtraClaims {
        public static final String ID = "id";
        public static final String EMAIL = "email";
        public static final String ROLE = "role";
    }

    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(Duration.ofDays(refreshExpiresInDays)))) // TODO: заменить на другое значение
                .signWith(getRefreshSigningKey())
                .compact();
    }

    /**
     * Генерация токена
     *
     * @param user данные пользователя
     * @return токен
     */
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(ExtraClaims.ID, user.getId());
        claims.put(ExtraClaims.EMAIL, user.getEmail());
        claims.put(ExtraClaims.ROLE, user.getRole());
        claims.put("pinus", "pinus");
        return generateAccessToken(claims, user);
    }

    /**
     * Генерация токена
     *
     * @param extraClaims дополнительные данные
     * @param user данные пользователя
     * @return токен
     */
    private String generateAccessToken(Map<String, Object> extraClaims, User user) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(user.getUsername())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(accessExpiresInMinutes))))
                .signWith(getAccessSigningKey())
                .compact();
    }

    public User getUserDetails(String token) {
        final var claims = extractAllClaims(token);
        return getUserByClaims(claims);
    }

    private User getUserByClaims(Claims claims) {
        return new User(
                null,
                claims.getSubject(),
                claims.get(ExtraClaims.EMAIL, String.class),
                null,
                Role.of(claims.get(ExtraClaims.ROLE, String.class))
        );
    }

    public boolean isRefreshTokenValid(String token) {
        try {
             Jwts.parser()
                    .verifyWith(getRefreshSigningKey())
                    .build()
                    .parseSignedClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }

        return true;
    }

    /**
     * Извлечение всех данных из токена
     *
     * @param token токен
     * @return данные
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getAccessSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Получение ключа для подписи токена
     *
     * @return ключ
     */
    private SecretKey getAccessSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessSigningKey));
    }

    private SecretKey getRefreshSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(refreshSigningKey));
    }
}
