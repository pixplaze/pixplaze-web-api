package com.pixplaze.api.web.service.auth;

import io.jsonwebtoken.Claims;

public interface JwtService<U> {
    Claims extractClaims(String token);
    String generate(U user);
}
