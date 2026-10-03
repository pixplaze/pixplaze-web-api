package com.pixplaze.api.web.configuration;

import com.pixplaze.api.web.configuration.properties.DeviceAuthorizationProperties;
import com.pixplaze.api.web.service.auth.device.InMemoryDeviceAuthorizationStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/**
 * Сборка device-flow (RFC 8628): хранилище и его уборка. Параметры — в
 * {@link DeviceAuthorizationProperties}, их потребители получают рекорд напрямую.
 *
 * <p>Стор создаётся здесь, а не аннотацией на классе, по двум причинам. Первая: он принимает
 * {@link Clock}, чтобы тесты управляли временем и работали без контекста Spring. Вторая: уборка
 * памяти — свойство именно in-memory реализации, и держать её рядом с объявлением бина честнее,
 * чем прятать в самом сторе. При переезде на Redis отсюда уходят оба бина сразу: TTL освобождает
 * память сам.
 */
@Configuration
@EnableConfigurationProperties(DeviceAuthorizationProperties.class)
public class DeviceAuthorizationConfiguration {

    @Bean
    public InMemoryDeviceAuthorizationStore deviceAuthorizationStore() {
        return new InMemoryDeviceAuthorizationStore(Clock.systemUTC());
    }

    @Bean
    public DeviceAuthorizationStoreEvictor deviceAuthorizationStoreEvictor(
            InMemoryDeviceAuthorizationStore store,
            TaskScheduler taskScheduler,
            DeviceAuthorizationProperties properties
    ) {
        // Период уборки равен сроку жизни записи: уборка существует только потому, что записи
        // истекают, и отдельная настройка лишь позволила бы рассогласовать одно с другим.
        return new DeviceAuthorizationStoreEvictor(store, taskScheduler, properties.expiration());
    }

    /**
     * Периодическая уборка истёкших записей. На видимость записей не влияет — истечение проверяется
     * на каждом чтении, — поэтому сбой уборки означает лишь рост памяти, а не ожившую запись.
     */
    @Slf4j
    @RequiredArgsConstructor
    public static class DeviceAuthorizationStoreEvictor {

        private final InMemoryDeviceAuthorizationStore store;
        private final TaskScheduler taskScheduler;
        private final Duration period;

        private ScheduledFuture<?> task;

        @PostConstruct
        void schedule() {
            task = taskScheduler.scheduleWithFixedDelay(this::evictExpired, period);
        }

        @PreDestroy
        void cancel() {
            if (task != null) {
                task.cancel(false);
                task = null;
            }
        }

        private void evictExpired() {
            final var evicted = store.evictExpired();

            if (evicted > 0) {
                log.debug("Evicted {} expired device authorization record(s)", evicted);
            }
        }
    }
}
