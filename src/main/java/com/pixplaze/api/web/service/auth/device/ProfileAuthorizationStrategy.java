package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationContext;
import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.ext.data.auth.NoAuthorizationDetails;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.service.auth.ProfileAccessTokenService;
import com.pixplaze.api.web.service.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Авторизация пользователя на новом устройстве через уже авторизованное (RFC 8628, классический
 * device-flow «войти на ТВ/CLI, подтвердив в приложении»). Отдельного «запрашиваемого» профиля нет:
 * субъект токена — тот профиль, который одобрил запрос. Поэтому полезной нагрузки у этого флоу нет,
 * а проверки сводятся к наличию одобряющего (его гарантирует фабрика контекста).
 */
@Service
@RequiredArgsConstructor
public class ProfileAuthorizationStrategy implements DeviceAuthorizationStrategy<NoAuthorizationDetails, AuthorizationTokenInfo> {

    private final ProfileAccessTokenService profileAccessTokenService;
    private final RefreshTokenService refreshTokenService;

    /// Полезной нагрузки у этого флоу нет — что бы ни прислали, результат один.
    @Override
    public NoAuthorizationDetails parse(String clientId, Authority authority, String authorizationDetailsString) {
        return new NoAuthorizationDetails();
    }

    @Override
    public DeviceAuthorizationInfo describe(DeviceAuthorizationContext<NoAuthorizationDetails> context) {
        final var authority = context.authority();

        return new DeviceAuthorizationInfo(
                Authority.Role.USER.name(),
                context.status(),
                authority.source().code(),
                authority.targets(),
                authority.permissions(),
                Map.of()
        );
    }

    @Override
    public AuthorizationTokenInfo authorize(DeviceAuthorizationContext<NoAuthorizationDetails> context) {
        final var approver = context.approver();

        // Пользователь уже аутентифицирован (одобрил со своего устройства) — здесь он ПОЛУЧАЕТ права:
        // grant() (пока пустой — доработаем в задаче прав). Роли/src — из запрошенного scope (анти-эскалация),
        // аудитория — от уже аутентифицированного одобряющего (чтобы grant прошёл валидацию targets).
        approver.setAuthority(Authority.as(context.authority())
                .to(approver.getAuthority().targets())
                .grant());

        final var accessToken = profileAccessTokenService.issue(approver);
        final var refreshToken = refreshTokenService.issue(approver);

        return new AuthorizationTokenInfo(accessToken, refreshToken);
    }
}
