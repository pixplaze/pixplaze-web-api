package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.ext.data.auth.MinecraftPlayerAuthorizationDetails;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationSession;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.data.user.MinecraftPlayerPrincipal;
import com.pixplaze.api.web.exception.MinecraftPlayerAlreadyOwnedException;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.mapper.MinecraftPlayerMapper;
import com.pixplaze.api.web.service.MinecraftPlayerService;
import com.pixplaze.api.web.service.MinecraftServerService;
import com.pixplaze.api.web.service.auth.MinecraftPlayerAccessTokenService;
import com.pixplaze.api.web.service.auth.RefreshTokenService;
import com.pixplaze.api.web.util.AddressUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinecraftPlayerAuthorizationStrategy implements DeviceAuthorizationStrategy<MinecraftPlayerAuthorizationDetails, AuthorizationTokenInfo> {

    /// Привязанному игроку токен живёт сильно дольше обычного профиля: MC-клиент держит
    /// долгую сессию, а отзывать её можно через refresh-цепочку.
    private static final Duration MINECRAFT_PLAYER_ACCESS_TTL = Duration.ofDays(2);

    private final RefreshTokenService refreshTokenService;
    private final MinecraftPlayerAccessTokenService minecraftPlayerAccessTokenService;
    private final MinecraftPlayerService minecraftPlayerService;
    private final MinecraftServerService minecraftServerService;
    private final MinecraftPlayerMapper minecraftPlayerMapper;
    private final JsonMapper jsonMapper;

    @Override
    public DeviceAuthorizationInfo describe(DeviceAuthorizationSession<MinecraftPlayerAuthorizationDetails> session) {
        final var sessionState = session.getState();
        final var authorizationDetails = sessionState.authorizationDetails().orElseThrow(DeviceAuthorizationException::new);
        final var status = sessionState.status();
        final var authority = sessionState.authority();

        return new DeviceAuthorizationInfo(
                Authority.Role.MINECRAFT_PLAYER.name(),
                status,
                authority.source().code(),
                authority.targets(),
                authority.permissions(),
                minecraftPlayerMapper.toAuthorizationDetails(authorizationDetails)
        );
    }

    @Override
    public MinecraftPlayerAuthorizationDetails parse(String clientId, Authority authority, String authorizationDetailsString) {
        return jsonMapper.readValue(authorizationDetailsString, MinecraftPlayerAuthorizationDetails.class);
    }

    /**
     * Предварительная проверка (RFC 8628, до публикации сессии): данные игрока присутствуют.
     * {@code ipAddress} обязателен — он участвует в сверке с IP одобряющего в {@link #authorize}.
     * Невалидный {@code null} поднимется как {@code INVALID_REQUEST}.
     */
    @Override
    public void validate(DeviceAuthorizationSession<MinecraftPlayerAuthorizationDetails> session) {
        try {
            final var authorizationDetails = session.getState().authorizationDetails().orElseThrow(NullPointerException::new);
            validateAuthorizationDetails(authorizationDetails);
        } catch (NullPointerException e) {
            throw exceptionInvalidRequest(e);
        }
    }

    @Override
    @Transactional
    public AuthorizationTokenInfo authorize(DeviceAuthorizationSession<MinecraftPlayerAuthorizationDetails> session) {
        final var sessionState = session.getState();
        final var authorizationDetails = sessionState.authorizationDetails().orElseThrow(this::exceptionInvalidRequest);
        final var approverPrincipal = sessionState.profile().orElseThrow(this::exceptionInvalidGrant);

        // Checks if not linked player is in the same network with approverPrincipal
        if (!minecraftPlayerService.isProfileLinked(authorizationDetails.uuid()) && !AddressUtils.isIpv4Same(authorizationDetails.ipAddress(), approverPrincipal.getIpAddress())) {
            throw exceptionAccessDenied();
        }

        try {
            minecraftPlayerService.upsert(minecraftPlayerMapper.toEntity(authorizationDetails));
            minecraftPlayerService.linkProfile(authorizationDetails.uuid(), approverPrincipal.getId());

            // Фиксируем членство игрока на сервере (если он зарегистрирован) — ребро игрок↔сервер,
            // благодаря которому host попадает в aud токена профиля. Незарегистрированный сервер пропускаем.
            minecraftServerService.findByHost(authorizationDetails.host())
                    .ifPresentOrElse(server -> {
                        minecraftServerService.linkPlayer(
                                server.getId(),
                                authorizationDetails.uuid(),
                                Boolean.TRUE.equals(authorizationDetails.isOperator())
                        );
                        minecraftServerService.addFavorite(server.getId(), approverPrincipal.getId());
                    }, this::exceptionAccessDenied);

//            grantApproverRequestedRoles(approverPrincipal, sessionState.authority());

            // Субъектный принципал появляется только здесь — после успешной привязки игрока к профилю.
            final var subjectPrincipal = new MinecraftPlayerPrincipal();
            subjectPrincipal.setUuid(authorizationDetails.uuid());
            subjectPrincipal.setUsername(authorizationDetails.username());
            subjectPrincipal.setProfileId(approverPrincipal.getId());
            subjectPrincipal.setHost(authorizationDetails.host());
            // aud = host сервера, против которого авторизован игрок (targets ≡ aud).
            subjectPrincipal.setAuthority(Authority.as(sessionState.authority()).to(authorizationDetails.host()).grant());

            final var accessToken = minecraftPlayerAccessTokenService.issue(subjectPrincipal, MINECRAFT_PLAYER_ACCESS_TTL);
            final var refreshToken = refreshTokenService.issue(subjectPrincipal);
            return new AuthorizationTokenInfo(accessToken, refreshToken);
        } catch (MinecraftPlayerAlreadyOwnedException e) {
            // Игрок уже привязан к ДРУГОМУ профилю — связать с одобряющим нельзя.
            throw new DeviceAuthorizationException(DeviceAuthorizationError.ACCESS_DENIED);
        }
    }

    /// Наделяет одобряющего ролями, которые он разрешил получить MAD (поверх своих), чтобы профиль мог
    /// действовать как авторизованный игрок. Мутация in-memory — точка для следующей задачи (роли из связей).
    private void grantApproverRequestedRoles(ApplicationClientPrincipal approver, Authority requested) {
        final var roles = new ArrayList<>(approver.getAuthority().roles());
        requested.roles().forEach(role -> {
            if (!roles.contains(role)) {
                roles.add(role);
            }
        });
        approver.setAuthority(Authority.as(roles.toArray(new Authority.Role[0]))
                .from(approver.getAuthority().source())
                .to(approver.getAuthority().targets())
                .grant(approver.getAuthority().permissions()));
    }

    private void validateAuthorizationDetails(MinecraftPlayerAuthorizationDetails authorizationDetails) {
        Objects.requireNonNull(authorizationDetails, "'authorizationDetails' must not be null!");
        Objects.requireNonNull(authorizationDetails.uuid(), "'authorizationDetails.uuid' must not be null!");
        Objects.requireNonNull(authorizationDetails.username(), "'authorizationDetails.username' must not be null!");
        Objects.requireNonNull(authorizationDetails.ipAddress(), "'authorizationDetails.ipAddress' must not be null!");
        // host обязателен: он становится aud токена игрока (targets), без него токен некому адресовать.
        Objects.requireNonNull(authorizationDetails.host(), "'authorizationDetails.host' must not be null!");
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidRequest() {
        return new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_REQUEST);
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidRequest(Exception e) {
        return new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_REQUEST, e);
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidGrant() {
        return new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_GRANT);
    }

    private @NonNull DeviceAuthorizationException exceptionAccessDenied() {
        return new DeviceAuthorizationException(DeviceAuthorizationError.ACCESS_DENIED);
    }
}
