package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationContext;
import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.ext.data.auth.MinecraftPlayerAuthorizationDetails;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
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
    public DeviceAuthorizationInfo describe(DeviceAuthorizationContext<MinecraftPlayerAuthorizationDetails> context) {
        final var details = requireDetails(context);
        final var authority = context.authority();

        return new DeviceAuthorizationInfo(
                Authority.Role.MINECRAFT_PLAYER.name(),
                context.status(),
                authority.source().code(),
                authority.targets(),
                authority.permissions(),
                minecraftPlayerMapper.toAuthorizationDetails(details)
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
    public void validate(DeviceAuthorizationContext<MinecraftPlayerAuthorizationDetails> context) {
        try {
            validateAuthorizationDetails(Objects.requireNonNull(context.details()));
        } catch (NullPointerException e) {
            throw exceptionInvalidRequest(e);
        }
    }

    @Override
    @Transactional
    public AuthorizationTokenInfo authorize(DeviceAuthorizationContext<MinecraftPlayerAuthorizationDetails> context) {
        final var details = requireDetails(context);
        final var approver = context.approver();

        // Checks if not linked player is in the same network with approver
        if (false && !minecraftPlayerService.isProfileLinked(details.uuid()) && !AddressUtils.isIpv4Same(details.ipAddress(), approver.getIpAddress())) {
            throw exceptionAccessDenied();
        }

        try {
            minecraftPlayerService.upsert(minecraftPlayerMapper.toEntity(details));
            minecraftPlayerService.linkProfile(details.uuid(), approver.getId());

            // Фиксируем членство игрока на сервере (если он зарегистрирован) — ребро игрок↔сервер,
            // благодаря которому host попадает в aud токена профиля. Незарегистрированный сервер пропускаем.
            minecraftServerService.findByHost(details.host())
                    .ifPresentOrElse(server -> {
                        minecraftServerService.linkPlayer(
                                server.getId(),
                                details.uuid(),
                                Boolean.TRUE.equals(details.isOperator())
                        );
                        minecraftServerService.addFavorite(server.getId(), approver.getId());
                    }, this::exceptionAccessDenied);

            // Роль MINECRAFT_PLAYER + host в aud появляются у ПРОФИЛЯ не здесь, а при следующем выпуске
            // его токена (sign-in / refresh): applyAuthority выводит их из персистентных связей выше.

            // Субъектный принципал появляется только здесь — после успешной привязки игрока к профилю.
            final var subjectPrincipal = new MinecraftPlayerPrincipal();
            subjectPrincipal.setUuid(details.uuid());
            subjectPrincipal.setUsername(details.username());
            subjectPrincipal.setProfileId(approver.getId());
            subjectPrincipal.setHost(details.host());
            // aud = host сервера, против которого авторизован игрок (targets ≡ aud).
            subjectPrincipal.setAuthority(Authority.as(context.authority()).to(details.host()).grant());

            final var accessToken = minecraftPlayerAccessTokenService.issue(subjectPrincipal, MINECRAFT_PLAYER_ACCESS_TTL);
            final var refreshToken = refreshTokenService.issue(subjectPrincipal);
            return new AuthorizationTokenInfo(accessToken, refreshToken);
        } catch (MinecraftPlayerAlreadyOwnedException e) {
            // Игрок уже привязан к ДРУГОМУ профилю — связать с одобряющим нельзя.
            throw new DeviceAuthorizationException(DeviceAuthorizationError.ACCESS_DENIED);
        }
    }

    private MinecraftPlayerAuthorizationDetails requireDetails(DeviceAuthorizationContext<MinecraftPlayerAuthorizationDetails> context) {
        final var details = context.details();

        if (details == null) {
            throw exceptionInvalidRequest();
        }

        return details;
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

    private @NonNull DeviceAuthorizationException exceptionAccessDenied() {
        return new DeviceAuthorizationException(DeviceAuthorizationError.ACCESS_DENIED);
    }
}
