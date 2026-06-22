package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.DeviceResponseInfo;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationDecision;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationState;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationDecisionRequestInfo;
import com.pixplaze.api.web.data.user.ClientPrincipial;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MinecraftDeviceAuthorizationService implements DeviceAuthorizationService {
    private final DeviceAuthorizationSessionService sessionService;

    @Value("${app.web.gateway.url}")
    private String webApplicationUrl;

    @Override
    public DeviceResponseInfo authorize(String clientId, String scope, String authorizationDetails) {
        final var split = scope.split(":");
        final var source = Authority.Source.of(split[0]);
        final var role = Authority.Role.valueOf(split[1]);
        final var unauthorizedAuthority = Authority.as(role).from(source).unauthorized();

        final var deviceCode = sessionService.generateDeviceCode();
        final var deviceCodeHash = sessionService.hashDeviceCode(deviceCode);
        final var session = sessionService.createSession(clientId, deviceCodeHash, unauthorizedAuthority, authorizationDetails);
        final var userCode = session.getState().userCode();
        final var expiresIn = (int) sessionService.getExpiration().toSeconds();
        final var interval = (int) sessionService.getInterval().toSeconds();

        return new DeviceResponseInfo(
                deviceCode,
                userCode,
                expiresIn,
                interval,
                buildVerificationUrl(),
                buildVerificationUrl(userCode)
        );
    }

    @Override
    public DeviceAuthorizationResponse<?> poll(String clientId, String deviceCode) {
        // Хешируем пришедший от NAD код, чтобы сравнить с тем, что в базе
        final var session = sessionService.getSessionByDeviceCode(deviceCode);
        
        if (session == null || session.isExpired() || session.isExhausted()) {
            sessionService.removeSession(session); // Удаляем, если попыток не осталось
            return DeviceAuthorizationResponse.error("expired_token");
        }

        final var sessionState = session.getState();

        // ЗАЩИТА: Проверяем, что токен опрашивает именно тот clientId, который его запрашивал
        if (!sessionState.clientId().equals(clientId)) {
            return DeviceAuthorizationResponse.error("invalid_grant"); // Попытка подмены контекста
        }

        if (DeviceAuthorizationState.Status.PENDING.equals(sessionState.status())) {
            return DeviceAuthorizationResponse.error("authorization_pending");
        }

        if (DeviceAuthorizationState.Status.DENIED.equals(sessionState.status())) {
            sessionService.removeSession(session); // Удаляем заблокированную сессию
            return DeviceAuthorizationResponse.error("access_denied");
        }

        if (DeviceAuthorizationState.Status.APPROVED.equals(sessionState.status())) {
            try {
                final var response = session.getStrategy().grant(session); // TODO!
                sessionService.removeSession(session);
                return response;
            } catch (Exception ignored) { // TODO: Implement error log
                System.err.println(ignored);
                return DeviceAuthorizationResponse.error("server_error");
            }
        }

        return DeviceAuthorizationResponse.error("invalid_grant");
    }

    @Override
    public void approve(DeviceAuthorizationDecisionRequestInfo decision, ClientPrincipial clientPrincipial) {
        final var session = sessionService.getSessionByUserCode(decision.userCode());

        if (session == null) {
            return;
        }

        if (!session.getState().userCode().equals(decision.userCode())) {
            return;
        }

        if (DeviceAuthorizationDecision.DENY.equals(decision.decision())) {
            session.setState(session.getState().denied());
            return;
        }
        
        session.setState(session.getState().approved(clientPrincipial));
    }

    private String buildVerificationUrl() {
        return webApplicationUrl + "/auth";
    }

    private String buildVerificationUrl(String userCode) {
        return buildVerificationUrl() + "?userCode=" + userCode;
    }

    @Deprecated(forRemoval = true)
    public Map<String, Object> toRfc8628Map(DeviceResponseInfo deviceResponseInfo) {
        return Map.of(
                "device_code", deviceResponseInfo.deviceCode(),
                "user_code", deviceResponseInfo.userCode(),
                "expires_in", deviceResponseInfo.expiresIn(),
                "interval", deviceResponseInfo.interval(),
                "verification_uri", deviceResponseInfo.verificationUri(),
                "verification_uri_complete", deviceResponseInfo.verificationUriComplete()
        );
    }
}
