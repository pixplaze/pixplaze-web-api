package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.web.data.auth.RefreshTokenSubjectType;
import com.pixplaze.api.web.data.db.tables.pojos.RefreshToken;
import com.pixplaze.api.web.data.user.ClientPrincipal;
import com.pixplaze.api.web.data.user.MinecraftPlayerPrincipal;
import com.pixplaze.api.web.data.user.MinecraftServerPrincipal;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.exception.auth.InvalidRefreshTokenException;
import com.pixplaze.api.web.repository.RefreshTokenRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.service.server.event.MinecraftServerBanned;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;

import static com.pixplaze.api.web.util.CryptoUtils.hash;

/**
 * Opaque refresh-токены: клиенту отдаётся случайная строка, в БД хранится только
 * её SHA-256 хэш, тип субъекта и owner-ссылки. Роли, источник и аудиторию токен не хранит:
 * при обновлении они заново выводятся из БД по {@code subject_type}, поэтому снятые права
 * не переживают ротацию. Такой токен отзываемый (в отличие от JWT) и не покидает доверенную
 * зону — проверяется только здесь.
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
    private final MinecraftServerRepository minecraftServerRepository;

    @Getter
    private final Duration expirationTerm;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            MinecraftServerRepository minecraftServerRepository,
            @Value("${app.security.auth.token.refresh.expiration.days}") int expirationInDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.minecraftServerRepository = minecraftServerRepository;
        this.expirationTerm = Duration.ofDays(expirationInDays);
    }

    /**
     * Выпускает opaque refresh-токен, сохраняет его хэш и субъекта, возвращает сырой токен.
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
        final var refreshToken = new RefreshToken()
                .setTokenHash(hash(rawToken))
                .setExpiresAt(OffsetDateTime.now().plus(effectiveTtl));

        applySubject(refreshToken, principal);

        refreshTokenRepository.create(refreshToken);

        return rawToken;
    }

    /// Проставляет subject_type и owner-ссылки по типу принципала. У игрока, кроме uuid и профиля,
    /// запоминается сервер, через который он вошёл: из него refresh выводит роли и аудиторию.
    private void applySubject(RefreshToken token, ClientPrincipal clientPrincipal) {
        if (clientPrincipal instanceof ApplicationClientPrincipal profilePrincipal) {
            token.setSubjectType(RefreshTokenSubjectType.PROFILE)
                    .setProfileId(profilePrincipal.getId());
        } else if (clientPrincipal instanceof MinecraftServerPrincipal minecraftServerPrincipal) {
            token.setSubjectType(RefreshTokenSubjectType.MINECRAFT_SERVER)
                    .setMinecraftServerId(minecraftServerPrincipal.getMinecraftServerId());
        } else if (clientPrincipal instanceof MinecraftPlayerPrincipal minecraftPlayerPrincipal) {
            token.setSubjectType(RefreshTokenSubjectType.MINECRAFT_PLAYER)
                    .setMinecraftPlayerUuid(minecraftPlayerPrincipal.getUuid())
                    .setMinecraftServerId(minecraftPlayerPrincipal.getMinecraftServerId())
                    .setProfileId(minecraftPlayerPrincipal.getProfileId());
        } else {
            throw new IllegalArgumentException("Unsupported principal type: " + clientPrincipal.getClass());
        }
    }

    /**
     * Проверяет refresh-токен и ротирует его: отзывает текущий, выпускает новый.
     *
     * @return субъект для перевыпуска access-токена и новый сырой refresh-токен
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
        // Токены забаненного сервера (его собственные и его игроков) не обновляем и гасим все.
        final var serverId = current.getMinecraftServerId();
        if (serverId != null && minecraftServerRepository.isBanned(serverId)) {
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
                .setExpiresAt(OffsetDateTime.now().plus(expirationTerm));
        final var next = refreshTokenRepository.create(refreshToken);

        refreshTokenRepository.markRotated(current.getId(), next.getId());

        return new RotationResult(
                current.getSubjectType(),
                current.getProfileId(),
                current.getMinecraftServerId(),
                current.getMinecraftPlayerUuid(),
                newRawToken
        );
    }

    /// Гасит всю живую цепочку токенов субъекта (по типу — профиль/сервер/игрок).
    private void revokeAllForSubject(RefreshToken token) {
        switch (token.getSubjectType()) {
            case PROFILE -> refreshTokenRepository.revokeAllForProfile(token.getProfileId());
            case MINECRAFT_SERVER -> refreshTokenRepository.revokeAllForServer(token.getMinecraftServerId());
            case MINECRAFT_PLAYER -> refreshTokenRepository.revokeAllForPlayer(token.getMinecraftPlayerUuid());
        }
    }

    public void revoke(String rawToken) {
        refreshTokenRepository.revokeByHash(hash(rawToken));
    }

    /** Отзывает все живые refresh-токены профиля (выход со всех устройств). */
    public void revokeAllForProfile(Long profileId) {
        refreshTokenRepository.revokeAllForProfile(profileId);
    }

    /** Отзывает все живые refresh-токены сервера. */
    public void revokeAllForServer(Long minecraftServerId) {
        refreshTokenRepository.revokeAllForServer(minecraftServerId);
    }

    /// Бан сервера: синхронно, в транзакции бана — отзыв не переживёт его откат и не отстанет от него.
    @EventListener
    public void onServerBanned(MinecraftServerBanned event) {
        revokeAllForServer(event.minecraftServerId());
    }

    private String generateRawToken() {
        final var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Субъект refresh-токена: по нему права для нового access-токена выводятся из БД. */
    public record RotationResult(
            RefreshTokenSubjectType subjectType,
            Long profileId,
            Long minecraftServerId,
            java.util.UUID playerUuid,
            String refreshToken
    ) {}
}
