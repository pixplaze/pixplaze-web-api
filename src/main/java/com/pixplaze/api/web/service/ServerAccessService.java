package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.auth.MinecraftServerTargets;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftPlayer;
import com.pixplaze.api.web.data.dto.PermissionsHint;
import com.pixplaze.api.web.data.dto.PluginAccessTokenResponse;
import com.pixplaze.api.web.data.server.ServerMembership;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.data.user.PluginAccessPrincipal;
import com.pixplaze.api.web.exception.http.ForbiddenException;
import com.pixplaze.api.web.repository.MinecraftPlayerRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.service.auth.PluginAccessTokenService;
import com.pixplaze.api.web.service.auth.RolePermissionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Игровые права профиля на конкретном сервере. Роли не хранятся, а выводятся из БД:
 * {@code MINECRAFT_PLAYER} — игрок профиля состоит на сервере, {@code MINECRAFT_OPERATOR} — он
 * оператор ({@code is_operator}), {@code MINECRAFT_OWNER} — профиль в {@code owner_profile_id}.
 *
 * <p>Один источник игровых ролей для обмена на токен доступа к плагину, для подсказки фронту и для
 * проверок в эндпоинтах: {@code @PreAuthorize("@serverAccess.can(#id, 'sys.servers.delete')")}.
 */
@Slf4j
@Service("serverAccess")
@RequiredArgsConstructor
public class ServerAccessService {

    private final MinecraftServerRepository minecraftServerRepository;
    private final MinecraftPlayerRepository minecraftPlayerRepository;
    private final RolePermissionCache rolePermissionCache;
    private final PluginAccessTokenService pluginAccessTokenService;

    /// Игровые роли профиля на сервере; пусто — профиль на сервере никто.
    public List<Authority.Role> rolesOn(Long profileId, Long serverId) {
        return rolesOf(minecraftServerRepository.findMembership(profileId, serverId));
    }

    /// Подсказка с правами для интерфейса: системные права из токена профиля и игровые права по id
    /// каждого сервера, где у профиля есть роль.
    public PermissionsHint permissionsHint(ApplicationClientPrincipal profile) {
        final var servers = new LinkedHashMap<Long, Set<String>>();
        if (profile.getId() != null) {
            minecraftServerRepository.findMemberships(profile.getId()).forEach((serverId, membership) ->
                    servers.put(serverId, rolePermissionCache.permissionsOf(rolesOf(membership))));
        }
        return new PermissionsHint(rolePermissionCache.permissionsOf(profile.getAuthority().roles()), servers);
    }

    private static List<Authority.Role> rolesOf(ServerMembership membership) {
        final var roles = new ArrayList<Authority.Role>();
        if (membership.isMember()) {
            roles.add(Authority.Role.MINECRAFT_PLAYER);
        }
        if (membership.isOperator()) {
            roles.add(Authority.Role.MINECRAFT_OPERATOR);
        }
        if (membership.isOwner()) {
            roles.add(Authority.Role.MINECRAFT_OWNER);
        }
        return roles;
    }

    /// Игровые права профиля на сервере: роли из {@link #rolesOn}, источник AAD, аудитория — сервер.
    /// Пусто — у профиля на сервере нет ни одной роли.
    public Optional<Authority> authorityOn(Long profileId, Long serverId) {
        final var roles = rolesOn(profileId, serverId);
        if (roles.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Authority.as(roles.toArray(new Authority.Role[0]))
                .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
                .to(MinecraftServerTargets.of(serverId))
                .grant());
    }

    /// Обмен токена профиля на токен доступа к плагину сервера: игровые роли из {@link #authorityOn},
    /// {@code sub} — основной игрок профиля на сервере, его имя — для подписи веб-сообщений в чате. Токен профиля плагину не передаётся никогда.
    ///
    /// @throws ForbiddenException сервера нет, он забанен или у профиля на нём нет ни одной роли
    public PluginAccessTokenResponse issuePluginToken(Long profileId, Long serverId) {
        final var server = minecraftServerRepository.findById(serverId)
                .filter(minecraftServer -> minecraftServer.getBannedAt() == null)
                .orElseThrow(() -> new ForbiddenException("Server %d does not exist or is banned".formatted(serverId)));
        final var authority = authorityOn(profileId, server.getId())
                .orElseThrow(() -> new ForbiddenException("Profile %d has no role on server %d".formatted(profileId, serverId)));

        final var principal = new PluginAccessPrincipal();
        principal.setProfileId(profileId);
        principal.setMinecraftServerId(server.getId());
        final var playerUuid = minecraftServerRepository.findPrimaryPlayer(profileId, server.getId()).orElse(null);
        principal.setPlayerUuid(playerUuid);
        if (playerUuid != null) {
            principal.setPlayerName(minecraftPlayerRepository.findByUuid(playerUuid).map(MinecraftPlayer::getUsername).orElse(null));
        }
        principal.setAuthority(authority);

        return new PluginAccessTokenResponse(pluginAccessTokenService.issue(principal), pluginAccessTokenService.getExpiresInSeconds());
    }

    /// Может ли текущий пользователь выполнить операцию с правом {@code permission} над сервером.
    /// Объединяет системные права из токена (например, администратор) и игровые права на этом сервере.
    public boolean can(Long serverId, String permission) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof ApplicationClientPrincipal profile)
                || profile.getId() == null || serverId == null) {
            return false;
        }
        if (rolePermissionCache.implies(profile.getAuthority().roles(), permission)) {
            return true;
        }
        final var roles = rolesOn(profile.getId(), serverId);
        if (rolePermissionCache.implies(roles, permission)) {
            return true;
        }
        log.debug("Profile #{} lacks {} on server #{}: system roles {}, server roles {}",
                profile.getId(), permission, serverId, profile.getAuthority().roles(), roles);
        return false;
    }
}
