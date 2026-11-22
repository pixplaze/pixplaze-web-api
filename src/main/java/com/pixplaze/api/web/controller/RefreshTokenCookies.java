package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.configuration.ApplicationConfiguration;
import com.pixplaze.api.web.service.auth.RefreshTokenService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/// Кука браузерного refresh-токена: имя, область и флаги — в одном месте для всех эндпоинтов {@code /auth}.
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {
    public static final String NAME = "refreshToken";
    private static final String PATH = "/auth";
    private final ApplicationConfiguration applicationConfiguration;

    private boolean secure = true;
    private String sameSite = "Lax";

    @PostConstruct
    private void init() {
        if (applicationConfiguration.isDevelopment()) {
            secure = false;
            sameSite = "None";
        }
    }

    private final RefreshTokenService refreshTokenService;
    public ResponseCookie issue(String refreshToken) {
        return ResponseCookie.from(NAME, refreshToken)
                .httpOnly(true) // Защита от XSS (JS не сможет прочитать куку)
                .secure(secure)   // Только через HTTPS
                .sameSite(sameSite)
                .path(PATH)
                .maxAge(refreshTokenService.getExpirationTerm())
                .build();
    }

    /** Кука, удаляющая refresh-токен в браузере (maxAge=0). */
    public ResponseCookie clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH)
                .maxAge(0)
                .build();
    }
}
