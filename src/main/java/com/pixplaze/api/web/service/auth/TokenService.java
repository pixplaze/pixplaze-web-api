package com.pixplaze.api.web.service.auth;

import io.jsonwebtoken.Claims;

import java.time.Duration;

public interface TokenService<I> {
    Claims extractClaims(String token);
    String issue(I identity);

    /** Выпуск с явным временем жизни; {@code null} — использовать дефолтный (autowired) TTL. */
    String issue(I identity, Duration ttl);
}
