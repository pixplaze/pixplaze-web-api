package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationDetails;
import com.pixplaze.api.ext.data.auth.AuthorizationToken;

import com.pixplaze.api.ext.data.oauth.OAuthError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DeviceAuthorizationStrategyFactory {

    private final Map<Class<?>, DeviceAuthorizationStrategy<?, ?>> strategies;

    // Spring автоматически внедрит сюда абсолютно все бины, реализующие DeviceAuthorizationStrategy
    public DeviceAuthorizationStrategyFactory(List<DeviceAuthorizationStrategy<?, ?>> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        AopUtils::getTargetClass,
                        Function.identity()
                ));
    }

    /**
     * Возвращает стратегию по ее строковому маркеру.
     * Параметр <T> гарантирует приведение к нужному типу дженерика на вызывающей стороне.
     */
    @SuppressWarnings("unchecked")
    public <A extends AuthorizationDetails> DeviceAuthorizationStrategy<A, AuthorizationToken> of(Authority authority) {
        if (authority.from(Authority.Source.MINECRAFT_AUTHORIZED_DEVICE)) {
            if (authority.is(Authority.Role.MINECRAFT_SERVER)) {
                return (DeviceAuthorizationStrategy<A, AuthorizationToken>) strategies.get(MinecraftServerAuthorizationStrategy.class);
            }

            if (authority.is(Authority.Role.MINECRAFT_PLAYER) || authority.is(Authority.Role.MINECRAFT_OPERATOR)) {
                return (DeviceAuthorizationStrategy<A, AuthorizationToken>) strategies.get(MinecraftPlayerAuthorizationStrategy.class);
            }
        }

        // Только USER: стратегия профиля выдаёт одобрившему запрошенную роль.
        if (authority.from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE) && authority.is(Authority.Role.USER)) {
            return (DeviceAuthorizationStrategy<A, AuthorizationToken>) strategies.get(ProfileAuthorizationStrategy.class);
        }

        // Scopes отсекает неподдерживаемые сочетания раньше; здесь — страховка.
        throw new DeviceAuthorizationException(OAuthError.INVALID_SCOPE);
    }
}
