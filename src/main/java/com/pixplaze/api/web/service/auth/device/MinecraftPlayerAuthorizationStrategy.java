package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.auth.MinecraftServerTargets;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationContext;
import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.ext.data.auth.MinecraftPlayerAuthorizationDetails;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.user.MinecraftPlayerPrincipal;
import com.pixplaze.api.web.exception.MinecraftPlayerAlreadyOwnedException;
import com.pixplaze.api.ext.data.oauth.OAuthError;
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

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinecraftPlayerAuthorizationStrategy implements DeviceAuthorizationStrategy<MinecraftPlayerAuthorizationDetails, AuthorizationTokenInfo> {

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
        final var authorizationDetails = minecraftPlayerMapper.toAuthorizationDetails(details);
        // Экрану подтверждения нужен адрес сервера, а игрок присылает только его id.
        minecraftServerService.findGameHost(details.minecraftServerId())
                .ifPresent(host -> authorizationDetails.put("host", AddressUtils.hostAndPort(host.address(), host.port())));

        return new DeviceAuthorizationInfo(
                Authority.Role.MINECRAFT_PLAYER.name(),
                context.status(),
                authority.source().code(),
                authority.targets(),
                authority.permissions(),
                authorizationDetails
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

        // Игрок входит только через зарегистрированный незабаненный сервер; проверяем до записей об игроке.
        final var server = minecraftServerService.findById(details.minecraftServerId())
                .filter(minecraftServer -> minecraftServer.getBannedAt() == null)
                .orElseThrow(this::exceptionAccessDenied);

        try {
            minecraftPlayerService.upsert(minecraftPlayerMapper.toEntity(details));
            minecraftPlayerService.linkProfile(details.uuid(), approver.getId());

            // Фиксируем членство игрока на сервере — ребро игрок↔сервер, благодаря которому сервер
            // попадает в aud токена профиля.
            minecraftServerService.linkPlayer(server.getId(), details.uuid(), Boolean.TRUE.equals(details.isOperator()));
            minecraftServerService.addFavorite(server.getId(), approver.getId());

            // Роль MINECRAFT_PLAYER + сервер в aud появляются у ПРОФИЛЯ не здесь, а при следующем выпуске
            // его токена (sign-in / refresh): applyAuthority выводит их из персистентных связей выше.

            // Субъектный принципал появляется только здесь — после успешной привязки игрока к профилю.
            final var subjectPrincipal = new MinecraftPlayerPrincipal();
            subjectPrincipal.setUuid(details.uuid());
            subjectPrincipal.setUsername(details.username());
            subjectPrincipal.setProfileId(approver.getId());
            subjectPrincipal.setMinecraftServerId(server.getId());
            // aud = сервер, против которого авторизован игрок (targets ≡ aud).
            subjectPrincipal.setAuthority(Authority.as(context.authority()).to(MinecraftServerTargets.of(server.getId())).grant());

            final var accessToken = minecraftPlayerAccessTokenService.issue(subjectPrincipal);
            final var refreshToken = refreshTokenService.issue(subjectPrincipal);
            return new AuthorizationTokenInfo(accessToken, refreshToken);
        } catch (MinecraftPlayerAlreadyOwnedException e) {
            // Игрок уже привязан к ДРУГОМУ профилю — связать с одобряющим нельзя.
            throw new DeviceAuthorizationException(OAuthError.ACCESS_DENIED);
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
        // serverId обязателен: из него строится aud токена игрока (targets), без него токен некому адресовать.
        Objects.requireNonNull(authorizationDetails.minecraftServerId(), "'authorizationDetails.serverId' must not be null!");
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidRequest() {
        return new DeviceAuthorizationException(OAuthError.INVALID_REQUEST);
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidRequest(Exception e) {
        return new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, e);
    }

    private @NonNull DeviceAuthorizationException exceptionAccessDenied() {
        return new DeviceAuthorizationException(OAuthError.ACCESS_DENIED);
    }
}
