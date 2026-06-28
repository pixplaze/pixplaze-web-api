package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.DeviceResponseInfo;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationDecision;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationSession;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationState;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationDecisionRequest;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDeviceAuthorizationService implements DeviceAuthorizationService {
    private final DeviceAuthorizationSessionService sessionService;
    private final DeviceAuthorizationStrategyFactory strategyFactory;

    @Value("${app.url.web.gateway}")
    private String webApplicationUrl;

    @Override
    public DeviceResponseInfo authorize(String clientId, String scope, String authorizationDetailsString) {
        final var unauthorizedAuthority = getUnauthorizedAuthority(scope);

        final var userCode = sessionService.generateUserCode();
        final var deviceCode = sessionService.generateDeviceCode();
        final var expiresIn = (int) sessionService.getExpiration().toSeconds();
        final var interval = (int) sessionService.getInterval().toSeconds();

        final var authorizationStrategy = strategyFactory.of(unauthorizedAuthority);
        final var authorizationDetails = authorizationStrategy.parse(clientId, unauthorizedAuthority, authorizationDetailsString);
        final var session = sessionService.createSession(clientId, userCode, deviceCode, unauthorizedAuthority, authorizationStrategy, authorizationDetails);

        authorizationStrategy.validate(session);
        sessionService.add(session);

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
    public Object poll(String clientId, String deviceCode) {
        if (clientId == null || clientId.isBlank() || deviceCode == null || deviceCode.isBlank()) {
            throw new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_REQUEST);
        }

        final var session = sessionService.getSessionByDeviceCode(deviceCode)
                .orElseThrow(this::exceptionExpiredToken);
        final var sessionState = session.getState();
        final var isBudgetExhausted = session.consumeAttempt();

        // Списываем попытку всегда (не через короткое замыкание ||), чтобы учёт бюджета
        // не зависел от исхода проверки на истечение. Бюджет исчерпан или сессия истекла — закрываем.
        if (session.isExpired() || isBudgetExhausted) {
            handleReject(session, DeviceAuthorizationError.EXPIRED_TOKEN);
        }

        if (!sessionState.clientId().equals(clientId)) {
            handleReject(session, DeviceAuthorizationError.INVALID_GRANT); // Попытка подмены контекста
        }

        if (DeviceAuthorizationState.Status.DENIED.equals(sessionState.status())) {
            handleReject(session, DeviceAuthorizationError.ACCESS_DENIED);
        }

        if (DeviceAuthorizationState.Status.PENDING.equals(sessionState.status())) {
            handlePending(session);
        }

        if (DeviceAuthorizationState.Status.APPROVED.equals(sessionState.status())) {
            return handleApprove(session);
        }

        throw new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_GRANT);
    }

    @Override
    public void approve(DeviceAuthorizationDecisionRequest decisionRequest, ApplicationClientPrincipal clientPrincipial) {
        final var session = sessionService.getSessionByUserCode(decisionRequest.userCode())
                .orElseThrow(this::exceptionExpiredToken);
        var sessionState = session.getState();

        if (!sessionState.userCode().equals(decisionRequest.userCode())) {
            session.consumeAttempt();
        }

        if (DeviceAuthorizationDecision.DENY.equals(decisionRequest.decision())) {
            sessionState = sessionState.denied();
        }

        if (DeviceAuthorizationDecision.ALLOW.equals(decisionRequest.decision())) {
            sessionState = sessionState.approved(clientPrincipial);
            // После одобрения сессия (и bearer-QR обратного логина) должна гаснуть в узком окне.
            session.capExpiry(sessionService.getGrantExpiration());
        }

        session.setState(sessionState);
    }

    @Override
    public DeviceAuthorizationInfo getAuthorizationInfo(String userCode, ApplicationClientPrincipal approver) {
        final DeviceAuthorizationSession<Object> session = sessionService.getSessionByUserCode(userCode)
                .orElseThrow(this::exceptionExpiredToken);

        if (session.isExpired()) {
            throw exceptionExpiredToken();
        }

        // Конверсия в DeviceAuthorizationInfo делегирована стратегии субъекта; в сессии ничего лишнего.
        return session.getStrategy().describe(session);
    }


    private static Authority getUnauthorizedAuthority(String scope) {
        if (scope == null || scope.trim().isBlank()) {
            return Authority.as(Authority.Role.USER)
                    .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
                    .unauthorized();
        }

        final var split = scope.split(":");
        final var source = Authority.Source.of(split[0]);
        final var role = Authority.Role.of(split[1]);
        return Authority.as(role).from(source).unauthorized();
    }

    private void handleReject(DeviceAuthorizationSession<Object> session, DeviceAuthorizationError error) {
        sessionService.remove(session);
        throw new DeviceAuthorizationException(error);
    }

    private Object handleApprove(DeviceAuthorizationSession<Object> session) {
        try {
            final var authorizationStrategy = session.getStrategy();
            final var response = authorizationStrategy.authorize(session);
            sessionService.remove(session);
            return response;
        } catch (DeviceAuthorizationException e) {
            throw e; // Доменная ошибка стратегии (например, access_denied) — пробрасываем как есть.
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new DeviceAuthorizationException(DeviceAuthorizationError.SERVER_ERROR, e);
        }
    }

    private void handlePending(DeviceAuthorizationSession<Object> session) {
        if (session.isPolledTooSoon(sessionService.getInterval())) {
            throw new DeviceAuthorizationException(DeviceAuthorizationError.SLOW_DOWN);
        }

        throw new DeviceAuthorizationException(DeviceAuthorizationError.AUTHORIZATION_PENDING);
    }

    private DeviceAuthorizationException exceptionExpiredToken() {
        return new DeviceAuthorizationException(DeviceAuthorizationError.EXPIRED_TOKEN);
    }

    private String buildVerificationUrl() {
        return webApplicationUrl + "/auth";
    }

    private String buildVerificationUrl(String userCode) {
        return buildVerificationUrl() + "?userCode=" + userCode;
    }
}
