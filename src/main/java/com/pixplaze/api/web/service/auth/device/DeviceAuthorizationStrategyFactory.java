package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
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
    public <A, T> DeviceAuthorizationStrategy<A, T> of(Authority authority) {
        if (authority.from(Authority.Source.MINECRAFT_AUTHORIZED_DEVICE)) {
            if (authority.is(Authority.Role.MINECRAFT_SERVER)) {
                return (DeviceAuthorizationStrategy<A, T>) strategies.get(MinecraftServerAuthorizationStrategy.class);
            }

            if (authority.is(Authority.Role.MINECRAFT_PLAYER) || authority.is(Authority.Role.MINECRAFT_OPERATOR)) {
                return (DeviceAuthorizationStrategy<A, T>) strategies.get(MinecraftPlayerAuthorizationStrategy.class);
            }
        }

        if (authority.from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)) {
            return (DeviceAuthorizationStrategy<A, T>) strategies.get(ProfileAuthorizationStrategy.class);
        }

        throw new IllegalStateException();
    }
}
