package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.web.repository.RolePermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Соответствие «роль → права» из {@code role_permission} в памяти. Таблица — справочник, который
 * меняет только повторяемая миграция, поэтому читается один раз при старте: запроса в БД на каждую
 * проверку нет. В токене лежат только роли, права web-api разворачивает здесь.
 *
 * <p>{@code *} в праве подставляется по сегментам: {@code sys.*} покрывает {@code sys.servers.delete},
 * {@code sys.*.read} — {@code sys.profile.read}, но не {@code sys.servers.list.read}.
 */
@Slf4j
@Service
public class RolePermissionCache {

    private static final String WILDCARD = "*";

    private final Map<Authority.Role, Set<String>> permissions = new EnumMap<>(Authority.Role.class);

    public RolePermissionCache(RolePermissionRepository rolePermissionRepository) {
        rolePermissionRepository.findAll().forEach((roleCode, codes) -> {
            try {
                permissions.put(Authority.Role.of(roleCode), Set.copyOf(codes));
            } catch (IllegalStateException e) {
                log.warn("Skipping permissions of unknown role code '{}'", roleCode);
            }
        });
    }

    /// Объединение прав ролей, отсортированное (стабильный вид для ответов фронту).
    public Set<String> permissionsOf(Collection<Authority.Role> roles) {
        final var result = new TreeSet<String>();
        roles.forEach(role -> result.addAll(permissions.getOrDefault(role, Set.of())));
        return result;
    }

    /// {@code true}, если хотя бы одно право ролей покрывает {@code required}.
    public boolean implies(Collection<Authority.Role> roles, String required) {
        return roles.stream()
                .flatMap(role -> permissions.getOrDefault(role, Set.of()).stream())
                .anyMatch(granted -> matches(granted, required));
    }

    /// Покрывает ли выданное право {@code granted} требуемое {@code required}. {@code *} в середине —
    /// ровно один сегмент, последним — любой непустой остаток.
    static boolean matches(String granted, String required) {
        final var grantedSegments = granted.split("\\.");
        final var requiredSegments = required.split("\\.");
        for (int i = 0; i < grantedSegments.length; i++) {
            final var segment = grantedSegments[i];
            final var isLast = i == grantedSegments.length - 1;
            if (WILDCARD.equals(segment) && isLast) {
                return requiredSegments.length > i;
            }
            if (i >= requiredSegments.length) {
                return false;
            }
            if (!WILDCARD.equals(segment) && !segment.equals(requiredSegments[i])) {
                return false;
            }
        }
        return grantedSegments.length == requiredSegments.length;
    }
}
