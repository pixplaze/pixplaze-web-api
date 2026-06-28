package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.Authority;
import io.jsonwebtoken.Claims;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Общая проекция {@link Authority} ↔ claims токена и имена identity-claims. Используется
 * и на записи (token-сервисы, {@link AbstractClientTokenService#buildClaims}), и на чтении
 * (static {@code toPrincipal} сервисов + {@code ClientPrincipalReader}), чтобы кодек ролей/
 * источника/прав не дублировался.
 */
final class AccessTokenClaims {

    /** id профиля. */
    static final String PID = "pid";
    static final String EMAIL = "email";

    /** Пишет роли ({@code rls}), источник ({@code src}) и права ({@code perms}, если непусты). */
    static void writeAuthority(Map<String, Object> claims, Authority authority) {
        claims.put(Authority.Claims.ROLE, authority.roles().stream().map(Authority.Role::code).toList());
        claims.put(Authority.Claims.SOURCE, authority.source().code());
        claims.put(Authority.Claims.PERMISSIONS, authority.permissions());
    }

    /** Восстанавливает {@link Authority} из claims: роли/источник/аудитория/права. */
    static Authority readAuthority(Claims claims) {
        final var roleCodes = (List<?>) claims.get(Authority.Claims.ROLE);
        final var roles = roleCodes.stream()
                .map(code -> Authority.Role.of((String) code))
                .toArray(Authority.Role[]::new);
        final var audience = claims.getAudience();
        return Authority
                .as(roles)
                .from(Authority.Source.of((String) claims.get(Authority.Claims.SOURCE)))
                .to(audience == null ? List.of() : new ArrayList<>(audience))
                .grant(readPermissions(claims));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> minecraftContext(Claims claims) {
        final var mc = claims.get(Authority.Claims.MINECRAFT_CONTEXT, Map.class);
        return mc == null ? Map.of() : (Map<String, Object>) mc;
    }

    static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    @SuppressWarnings("unchecked")
    private static List<String> readPermissions(Claims claims) {
        final var permissions = claims.get(Authority.Claims.PERMISSIONS, List.class);
        return permissions == null ? List.of() : (List<String>) permissions;
    }
}
