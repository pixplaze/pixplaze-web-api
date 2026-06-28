package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.auth.RefreshTokenSubjectType;
import com.pixplaze.api.web.data.db.tables.pojos.RefreshToken;
import com.pixplaze.api.web.data.server.MinecraftServerStatus;
import com.pixplaze.api.web.data.user.ClientPrincipal;
import com.pixplaze.api.web.data.user.MinecraftPlayerPrincipal;
import com.pixplaze.api.web.data.user.MinecraftServerPrincipal;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.exception.auth.InvalidRefreshTokenException;
import com.pixplaze.api.web.repository.RefreshTokenRepository;
import com.pixplaze.api.web.service.MinecraftServerService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static com.pixplaze.api.web.util.CryptoUtils.hash;

/**
 * Opaque refresh-токены: клиенту отдаётся случайная строка, в БД хранится только
 * её SHA-256 хэш и контекст для перевыпуска access-токена. Такой токен отзываемый
 * (в отличие от JWT) и не покидает доверенную зону — проверяется только здесь.
 *
 * <p>При обновлении выполняется ротация: старый токен отзывается и связывается с
 * новым ({@code replaced_by}); повторное предъявление уже отозванного токена
 * трактуется как компрометация — отзывается вся цепочка профиля.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository refreshTokenRepository;
    private final MinecraftServerService minecraftServerService;

    @Getter
    private final Duration expirationTerm;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            MinecraftServerService minecraftServerService,
            @Value("${app.security.auth.token.refresh.expiration.days}") int expirationInDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.minecraftServerService = minecraftServerService;
        this.expirationTerm = Duration.ofDays(expirationInDays);
    }

    /**
     * Выпускает opaque refresh-токен, сохраняет его хэш и контекст, возвращает сырой токен.
     * Тип субъекта и owner-ссылка выводятся из типа принципала.
     */
    public String issue(ClientPrincipal principal) {
        return issue(principal, null);
    }

    /**
     * @param ttl явное время жизни токена; {@code null} — использовать дефолтный (autowired) срок
     */
    public String issue(ClientPrincipal principal, Duration ttl) {
        final var effectiveTtl = ttl != null ? ttl : expirationTerm;
        final var rawToken = generateRawToken();
        final var authority = principal.getAuthority();
        final var refreshToken = new RefreshToken()
                .setTokenHash(hash(rawToken))
                .setAuthSource(authority.source().code())
                .setAuthRoles(encodeRoles(authority.roles()))
                .setAuthTargets(encodeTargets(authority.targets()))
                .setExpiresAt(OffsetDateTime.now().plus(effectiveTtl));

        applySubject(refreshToken, principal);

        refreshTokenRepository.create(refreshToken);

        return rawToken;
    }

    /// Проставляет subject_type и соответствующую owner-ссылку по типу принципала.
    private void applySubject(RefreshToken token, ClientPrincipal clientPrincipal) {
        if (clientPrincipal instanceof ApplicationClientPrincipal profilePrincipal) {
            token.setSubjectType(RefreshTokenSubjectType.PROFILE)
                    .setProfileId(profilePrincipal.getId());
        } else if (clientPrincipal instanceof MinecraftServerPrincipal minecraftServerPrincipal) {
            token.setSubjectType(RefreshTokenSubjectType.MINECRAFT_SERVER)
                    .setMinecraftServerId(minecraftServerPrincipal.getServerId());
        } else if (clientPrincipal instanceof MinecraftPlayerPrincipal minecraftPlayerPrincipal) {
            final var type = clientPrincipal.getAuthority().is(Authority.Role.MINECRAFT_OPERATOR)
                    ? RefreshTokenSubjectType.MINECRAFT_OPERATOR
                    : RefreshTokenSubjectType.MINECRAFT_PLAYER;
            token.setSubjectType(type)
                    .setMinecraftPlayerUuid(minecraftPlayerPrincipal.getUuid())
                    .setProfileId(minecraftPlayerPrincipal.getProfileId());
        } else {
            throw new IllegalArgumentException("Unsupported principal type: " + clientPrincipal.getClass());
        }
    }

    /**
     * Проверяет refresh-токен и ротирует его: отзывает текущий, выпускает новый.
     *
     * @return контекст для перевыпуска access-токена и новый сырой refresh-токен
     * @throws InvalidRefreshTokenException если токен неизвестен, истёк или уже отозван (reuse)
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        final var current = refreshTokenRepository.findByHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("unknown"));

        if (current.getRevokedAt() != null) {
            // Предъявлен уже отозванный токен — вероятная кража, гасим всю цепочку субъекта.
            revokeAllForSubject(current);
            throw new InvalidRefreshTokenException("reuse_detected");
        }
        if (current.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidRefreshTokenException("expired");
        }
        // Серверный токен забаненного сервера не обновляем и гасим всю его цепочку.
        final var serverId = current.getMinecraftServerId();
        if (serverId != null && minecraftServerService.getStatus(serverId).orElse(null) == MinecraftServerStatus.BANNED) {
            refreshTokenRepository.revokeAllForServer(serverId);
            throw new InvalidRefreshTokenException("server_banned");
        }

        final var newRawToken = generateRawToken();
        final var refreshToken = new RefreshToken()
                .setTokenHash(hash(newRawToken))
                .setSubjectType(current.getSubjectType())
                .setProfileId(current.getProfileId())
                .setMinecraftServerId(current.getMinecraftServerId())
                .setMinecraftPlayerUuid(current.getMinecraftPlayerUuid())
                .setAuthSource(current.getAuthSource())
                .setAuthRoles(current.getAuthRoles())
                .setAuthTargets(current.getAuthTargets())
                .setExpiresAt(OffsetDateTime.now().plus(expirationTerm));
        final var next = refreshTokenRepository.create(refreshToken);

        refreshTokenRepository.markRotated(current.getId(), next.getId());

        return new RotationResult(
                current.getSubjectType(),
                current.getProfileId(),
                current.getMinecraftServerId(),
                current.getMinecraftPlayerUuid(),
                Authority.Source.of(current.getAuthSource()),
                decodeRoles(current.getAuthRoles()),
                decodeTargets(current.getAuthTargets()),
                newRawToken
        );
    }

    /// Гасит всю живую цепочку токенов субъекта (по типу — профиль/сервер/игрок).
    private void revokeAllForSubject(RefreshToken token) {
        switch (token.getSubjectType()) {
            case PROFILE -> refreshTokenRepository.revokeAllForProfile(token.getProfileId());
            case MINECRAFT_SERVER -> refreshTokenRepository.revokeAllForServer(token.getMinecraftServerId());
            case MINECRAFT_PLAYER, MINECRAFT_OPERATOR -> refreshTokenRepository.revokeAllForPlayer(token.getMinecraftPlayerUuid());
        }
    }

    /// Кодирование набора ролей для хранения в одной колонке auth_role (CSV коротких кодов).
    /// Фаза 3 заменит на отдельную колонку auth_roles.
    private static String encodeRoles(List<Authority.Role> roles) {
        return roles.stream().map(Authority.Role::code).collect(Collectors.joining(","));
    }

    private static List<Authority.Role> decodeRoles(String encoded) {
        return Arrays.stream(encoded.split(","))
                .filter(code -> !code.isBlank())
                .map(Authority.Role::of)
                .toList();
    }

    /// Кодирование/декодирование targets (≡ aud) в одну колонку auth_targets (CSV хостов/зон).
    /// Хосты не содержат запятых, поэтому CSV безопасен.
    private static String encodeTargets(List<String> targets) {
        return String.join(",", targets);
    }

    private static List<String> decodeTargets(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        return Arrays.stream(encoded.split(","))
                .filter(target -> !target.isBlank())
                .toList();
    }

    public void revoke(String rawToken) {
        refreshTokenRepository.revokeByHash(hash(rawToken));
    }

    /** Отзывает все живые refresh-токены профиля (выход со всех устройств). */
    public void revokeAllForProfile(Long profileId) {
        refreshTokenRepository.revokeAllForProfile(profileId);
    }

    /** Отзывает все живые refresh-токены сервера (например, при бане). */
    public void revokeAllForServer(Long minecraftServerId) {
        refreshTokenRepository.revokeAllForServer(minecraftServerId);
    }

    private String generateRawToken() {
        final var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Контекст, восстанавливаемый из refresh-токена для перевыпуска access-токена. */
    public record RotationResult(
            RefreshTokenSubjectType subjectType,
            Long profileId,
            Long serverId,
            java.util.UUID playerUuid,
            Authority.Source source,
            List<Authority.Role> roles,
            List<String> targets,
            String refreshToken
    ) {}
}
