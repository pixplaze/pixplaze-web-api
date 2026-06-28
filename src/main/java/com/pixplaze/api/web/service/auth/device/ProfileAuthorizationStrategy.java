package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationSession;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.service.auth.ProfileAccessTokenService;
import com.pixplaze.api.web.service.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Авторизация пользователя на новом устройстве через уже авторизованное (RFC 8628, классический
 * device-flow «войти на ТВ/CLI, подтвердив в приложении»). Отдельного «запрашиваемого» профиля нет:
 * субъект токена — тот профиль, который одобрил запрос. Поэтому device-Details отсутствуют, а
 * проверки сводятся к наличию одобряющего.
 */
@Service
@RequiredArgsConstructor
public class ProfileAuthorizationStrategy implements DeviceAuthorizationStrategy<Void, AuthorizationTokenInfo> {

    private final ProfileAccessTokenService profileAccessTokenService;
    private final RefreshTokenService refreshTokenService;

    @Override
    public DeviceAuthorizationInfo describe(DeviceAuthorizationSession<Void> deviceAuthorizationSession) {
        final var sessionState = deviceAuthorizationSession.getState();
        final var authority = sessionState.authority();

        return new DeviceAuthorizationInfo(
                Authority.Role.USER.name(),
                sessionState.status(),
                authority.source().code(),
                authority.targets(),
                authority.permissions(),
                Map.of()
        );
    }

    @Override
    public AuthorizationTokenInfo authorize(DeviceAuthorizationSession<Void> session) {
        final var state = session.getState();
        final var approverPrincipal = state.profile().orElseThrow(this::exceptionInvalidGrant);

        // Пользователь уже аутентифицирован (одобрил со своего устройства) — здесь он ПОЛУЧАЕТ права:
        // grant() (пока пустой — доработаем в задаче прав). Роли/src — из запрошенного scope (анти-эскалация),
        // аудитория — от уже аутентифицированного одобряющего (чтобы grant прошёл валидацию targets).
        approverPrincipal.setAuthority(Authority.as(state.authority())
                .to(approverPrincipal.getAuthority().targets())
                .grant());

        final var accessToken = profileAccessTokenService.issue(approverPrincipal);
        final var refreshToken = refreshTokenService.issue(approverPrincipal);
        return new AuthorizationTokenInfo(accessToken, refreshToken);
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidGrant() {
        return new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_GRANT);
    }
}
