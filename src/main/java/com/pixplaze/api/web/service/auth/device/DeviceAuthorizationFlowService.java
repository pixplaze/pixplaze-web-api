package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.auth.AuthorizationToken;
import com.pixplaze.api.ext.data.auth.DeviceResponseInfo;
import com.pixplaze.api.web.configuration.properties.DeviceAuthorizationProperties;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationStatus;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationDecisionRequest;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.service.auth.device.DeviceAuthorizationStore.DecisionOutcome;
import com.pixplaze.api.web.service.auth.device.model.ApproverDecision;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationRequest;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.jspecify.annotations.Nullable;


/**
 * Протокол device-flow (RFC 8628) поверх {@link DeviceAuthorizationStore}.
 *
 * <p>Сервис переводит исходы операций стора в ответы протокола и не держит состояния: всё, что
 * меняется конкурентно, меняется в сторе одной операцией. Работа с конкретным типом субъекта
 * делегируется стратегиям, сборка их контекста — {@link DeviceAuthorizationStrategyInvoker}.
 *
 * <p>Обращения к стору на запрос: создание — 2 (резерв user-кода и запись), опрос — 1,
 * выдача — 2 (опрос и захват), подтверждение — 2 (поиск и решение), экран подтверждения — 1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAuthorizationFlowService implements DeviceAuthorizationService {

    /// Сколько раз пробовать выпустить свободный user-код. 8 символов из 32 — 40 бит, так что
    /// коллизия с живым запросом — редкость, а три подряд — признак поломки, а не невезения.
    private static final int USER_CODE_ALLOCATION_ATTEMPTS = 3;

    private final DeviceAuthorizationStore store;
    private final DeviceAuthorizationStrategyInvoker strategyInvoker;
    private final DeviceCodeGenerator codeGenerator;
    private final DeviceAuthorizationProperties properties;

    @Override
    public DeviceResponseInfo authorize(String clientId, String scope, @Nullable String details) {
        final var deviceCode = codeGenerator.generateDeviceCode();
        final var request = new DeviceAuthorizationRequest(
                clientId,
                codeGenerator.generateUserCode(),
                codeGenerator.hashDeviceCode(deviceCode),
                scope
        );

        // Проверяем до публикации: непригодный запрос хранилище видеть не должно.
        strategyInvoker.forValidation(request, details).validate();

        final var published = publish(request, details);

        return DeviceResponseInfo.builder()
                .deviceCode(deviceCode)
                .userCode(published.userCode())
                .expiresIn(properties.expiration().toSeconds())
                .interval(properties.pollingInterval().toSeconds())
                .verificationUri(properties.verificationUri())
                .verificationUriComplete(properties.verificationUriComplete(published.userCode()))
                .build();
    }

    @Override
    public AuthorizationToken poll(String clientId, String deviceCode) {
        if (isBlank(clientId) || isBlank(deviceCode)) {
            throw error(DeviceAuthorizationError.INVALID_REQUEST);
        }

        final var deviceCodeHash = codeGenerator.hashDeviceCode(deviceCode);
        final var consumed = store.readAndConsumeAttempt(deviceCodeHash, properties.pollingInterval());

        if (consumed.isEmpty()) {
            log.debug("Device authorization polled after expiry or with an unknown device code");
            throw error(DeviceAuthorizationError.EXPIRED_TOKEN);
        }

        final var attempt = consumed.get();
        final var state = attempt.state();

        if (!state.request().clientId().equals(clientId)) {
            throw reject(deviceCodeHash, DeviceAuthorizationError.INVALID_GRANT); // Попытка подмены контекста
        }

        if (state.attemptsLeft() < 0) {
            log.info("Device authorization polling budget exhausted: clientId={}", state.request().clientId());
            throw reject(deviceCodeHash, DeviceAuthorizationError.EXPIRED_TOKEN);
        }

        if (state.status() == DeviceAuthorizationStatus.PENDING) {
            if (attempt.isTooSoon()) {
                log.info(
                        "Device authorization polled before the interval elapsed: clientId={}",
                        state.request().clientId()
                );
                throw error(DeviceAuthorizationError.SLOW_DOWN);
            }

            throw error(DeviceAuthorizationError.AUTHORIZATION_PENDING);
        }

        if (state.status() == DeviceAuthorizationStatus.DENIED) {
            throw reject(deviceCodeHash, DeviceAuthorizationError.ACCESS_DENIED);
        }

        return grant(deviceCodeHash);
    }

    @Override
    public void approve(DeviceAuthorizationDecisionRequest decisionRequest, ApplicationClientPrincipal approver) {
        if (decisionRequest.decision() == null) {
            throw error(DeviceAuthorizationError.INVALID_REQUEST);
        }

        final var state = findAwaitingDecision(decisionRequest.userCode());
        final var outcome = store.putDecisionIfAbsent(
                        state.request().deviceCodeHash(),
                        ApproverDecision.of(decisionRequest.decision(), approver),
                        properties.decisionTtl())
                .orElseThrow(() -> error(DeviceAuthorizationError.EXPIRED_TOKEN));

        if (outcome == DecisionOutcome.ALREADY_DECIDED) {
            throw error(DeviceAuthorizationError.INVALID_GRANT);
        }
    }

    @Override
    public DeviceAuthorizationInfo getAuthorizationInfo(String userCode, ApplicationClientPrincipal approver) {
        return strategyInvoker.forDescription(findAwaitingDecision(userCode)).describe();
    }

    /// Запрос, по которому ещё можно принять решение и выдать токены. Иначе — ошибка: экран
    /// подтверждения не должен показывать запрос, одобрение которого уже ни к чему не приведёт.
    private DeviceAuthorizationState findAwaitingDecision(String userCode) {
        final var state = store.findByUserCode(userCode)
                .orElseThrow(() -> error(DeviceAuthorizationError.EXPIRED_TOKEN));

        if (state.decision() != null) {
            throw error(DeviceAuthorizationError.INVALID_GRANT);
        }

        if (!state.canBePolledAgain()) {
            throw error(DeviceAuthorizationError.EXPIRED_TOKEN);
        }

        return state;
    }

    /// Выдача токенов. Право на выдачу забирается до неё: из параллельных опросов её выполнит
    /// ровно один, проигравшие получат {@code expired_token}. Размен осознанный — при сбое
    /// выдачи запрос потерян и флоу начинается заново, зато второй выдачи не бывает. Побочные
    /// эффекты стратегий транзакционны, поэтому полурегистрации после сбоя не остаётся.
    private AuthorizationToken grant(String deviceCodeHash) {
        final var claimed = store.takeAndRemove(deviceCodeHash)
                .orElseThrow(() -> error(DeviceAuthorizationError.EXPIRED_TOKEN));

        try {
            return strategyInvoker.forGrant(claimed).authorize();
        } catch (DeviceAuthorizationException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Device authorization grant failed", e);
            throw new DeviceAuthorizationException(DeviceAuthorizationError.SERVER_ERROR, e);
        }
    }

    /// Размещает запрос, при коллизии user-кода выпуская новый.
    private DeviceAuthorizationRequest publish(DeviceAuthorizationRequest request, @Nullable String details) {
        var candidate = request;

        for (var attempt = 1; ; attempt++) {
            if (store.tryCreate(candidate, details, properties.attemptBudget(), properties.expiration())) {
                return candidate;
            }

            if (attempt == USER_CODE_ALLOCATION_ATTEMPTS) {
                throw new IllegalStateException("Could not allocate a free user code in %d attempts"
                        .formatted(USER_CODE_ALLOCATION_ATTEMPTS));
            }

            candidate = candidate.withUserCode(codeGenerator.generateUserCode());
        }
    }

    private DeviceAuthorizationException reject(String deviceCodeHash, DeviceAuthorizationError error) {
        store.remove(deviceCodeHash);
        return error(error);
    }

    private static DeviceAuthorizationException error(DeviceAuthorizationError error) {
        return new DeviceAuthorizationException(error);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
