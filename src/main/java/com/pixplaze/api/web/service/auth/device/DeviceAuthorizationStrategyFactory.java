package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
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
                        DeviceAuthorizationStrategy::getClass,
                        Function.identity()
                ));
    }

    /**
     * Возвращает стратегию по ее строковому маркеру.
     * Параметр <T> гарантирует приведение к нужному типу дженерика на вызывающей стороне.
     */
    @SuppressWarnings("unchecked")
    public <D, T> DeviceAuthorizationStrategy<D, T> of(Authority authority) {
        if (authority.from(Authority.Source.MINECRAFT_AUTHORIZED_DEVICE) && authority.is(Authority.Role.USER)) {
            return (DeviceAuthorizationStrategy<D, T>) strategies.get(MinecraftPlayerAuthorizationStrategy.class);
        }

        throw new IllegalStateException();
    }
}
