package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.service.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/// Кука браузерного refresh-токена: имя, область и флаги — в одном месте для всех эндпоинтов {@code /auth}.
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {
    public static final String NAME = "refreshToken";
    // Scoped на /auth, чтобы ходить и на /auth/refresh, и на /auth/sign-out.
    private static final String PATH = "/auth";

    private final RefreshTokenService refreshTokenService;

    public ResponseCookie issue(String refreshToken) {
        return ResponseCookie.from(NAME, refreshToken)
                .httpOnly(true) // Защита от XSS (JS не сможет прочитать куку)
                .secure(true)   // Только через HTTPS
                .path(PATH)
                .maxAge(refreshTokenService.getExpirationTerm())
                .build();
    }

    /** Кука, удаляющая refresh-токен в браузере (maxAge=0). */
    public ResponseCookie clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(true)
                .path(PATH)
                .maxAge(0)
                .build();
    }
}
