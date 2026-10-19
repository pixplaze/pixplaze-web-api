package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.auth.AuthorityClaims;
import io.jsonwebtoken.Claims;

import java.util.Map;

/**
 * Имена identity-claims и помощники чтения контекста Minecraft. Кодек {@link Authority} ↔ claims
 * (роли, источник, аудитория) — общий для web-api и плагина, лежит в ext-api: {@link AuthorityClaims}.
 */
final class AccessTokenClaims {

    /** id профиля. */
    static final String PID = "pid";
    static final String EMAIL = "email";

    @SuppressWarnings("unchecked")
    static Map<String, Object> minecraftContext(Claims claims) {
        final var mc = claims.get(Authority.Claims.MINECRAFT_CONTEXT, Map.class);
        return mc == null ? Map.of() : (Map<String, Object>) mc;
    }

    static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
