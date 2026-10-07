package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.auth.AuthorizationDetails;
import com.pixplaze.api.ext.data.auth.AuthorizationToken;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationStatus;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.ext.data.oauth.OAuthError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.service.ProfileService;
import com.pixplaze.api.web.service.auth.device.model.ApproverDecision;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationContext;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationRequest;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationState;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Подготовка вызова стратегии: подобрать стратегию по привилегии и собрать ей
 * {@link DeviceAuthorizationContext} — разобрать детали, вывести статус, при выдаче загрузить
 * одобряющего.
 *
 * <p>Стратегия и контекст отдаются вместе ({@link StrategyCall}), а не по отдельности, потому что
 * они обязаны согласоваться по типу деталей {@code A}. Согласование это держится на приведении
 * внутри {@link DeviceAuthorizationStrategyFactory}, и пара существует затем, чтобы оно
 * происходило в одном месте, а не в каждом вызывающем методе.
 *
 * <p>Хранилище здесь не нужно: детали приходят вместе со снимком, а разбирает их сама стратегия.
 * Фабрика зависит только от того, что действительно приходится дочитывать, — от профилей.
 *
 * <p>Статус приходит готовым, а не выводится здесь: у снимка он уже есть
 * ({@link DeviceAuthorizationState#status()}), а неопубликованный запрос по определению
 * {@code PENDING} — записи, по которой могло бы быть решение, ещё не существует.
 */
@Component
@RequiredArgsConstructor
public class DeviceAuthorizationStrategyInvoker {

    private final DeviceAuthorizationStrategyFactory strategyFactory;
    private final ProfileService profileService;

    /** Вызов по запросу, который ещё не опубликован: проверка до создания. */
    public StrategyCall<?> forValidation(DeviceAuthorizationRequest request, @Nullable String details) {
        return build(request, details, DeviceAuthorizationStatus.PENDING, null);
    }

    /** Вызов по сохранённому запросу: экран подтверждения. */
    public StrategyCall<?> forDescription(DeviceAuthorizationState state) {
        return build(state.request(), state.details(), state.status(), null);
    }

    /**
     * Вызов для выдачи токенов, с загруженным одобряющим. Принципал грузится по сохранённому
     * id профиля, поэтому права читаются свежими на момент выдачи.
     */
    public StrategyCall<?> forGrant(DeviceAuthorizationState state) {
        final var decision = state.decision();

        if (decision == null) {
            throw new DeviceAuthorizationException(OAuthError.EXPIRED_TOKEN);
        }

        if (decision.status() != DeviceAuthorizationStatus.APPROVED) {
            throw new DeviceAuthorizationException(OAuthError.INVALID_GRANT);
        }

        return build(state.request(), state.details(), state.status(), loadApprover(decision));
    }

    private <A extends AuthorizationDetails> StrategyCall<A> build(
            DeviceAuthorizationRequest request,
            @Nullable String details,
            DeviceAuthorizationStatus status,
            @Nullable ApplicationClientPrincipal approver
    ) {
        final var authority = request.authority();
        final DeviceAuthorizationStrategy<A, AuthorizationToken> strategy = strategyFactory.of(authority);
        final A parsedDetails;
        try {
            parsedDetails = strategy.parse(request.clientId(), authority, details);
        } catch (JacksonException | IllegalArgumentException e) {
            // Детали присылает недоверенное устройство: нет, не JSON или не та форма — invalid_request.
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, e);
        }

        return new StrategyCall<>(strategy, new DeviceAuthorizationContext<>(authority, parsedDetails, status, approver));
    }

    private ApplicationClientPrincipal loadApprover(ApproverDecision decision) {
        final var approver = profileService.toApplicationClientPrincipal(
                profileService.getById(decision.approverProfileId()));
        approver.setIpAddress(decision.approverIpAddress());

        return approver;
    }

    /**
     * Стратегия вместе с готовым для неё контекстом. Снаружи параметр {@code A} не нужен: ни один
     * метод его не упоминает, поэтому пара используется как {@code StrategyCall<?>}.
     */
    public record StrategyCall<A extends AuthorizationDetails>(
            DeviceAuthorizationStrategy<A, AuthorizationToken> strategy,
            DeviceAuthorizationContext<A> context
    ) {

        public void validate() {
            strategy.validate(context);
        }

        public AuthorizationToken authorize() {
            return strategy.authorize(context);
        }

        public DeviceAuthorizationInfo describe() {
            return strategy.describe(context);
        }
    }
}
