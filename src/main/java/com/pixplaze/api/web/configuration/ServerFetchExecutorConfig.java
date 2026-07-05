package com.pixplaze.api.web.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Единственная точка выбора модели потоков для блокирующего fan-out работы с серверами
 * (Tier-2 пинги, pull-фолбэк Tier-3). Вся логика работает через {@link ExecutorService} и
 * per-task таймауты, не завязываясь на размер пула, поэтому переезд на Java 21 —
 * это замена тела этого бина на {@code Executors.newVirtualThreadPerTaskExecutor()},
 * без правки вызывающего кода.
 *
 * <p>Замечание по нагрузке: реальную конкуренцию к серверам ограничивает не этот пул, а
 * per-IP/subnet троттлинг «вежливого pull» (фаза 2); пул задаёт лишь верхнюю ёмкость.
 */
@Configuration
public class ServerFetchExecutorConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService serverFetchExecutor(
            @Value("${app.servers.fetch.threads:64}") int threads
    ) {
        // --- Java 17: ограниченный пул платформенных потоков ---
        final var counter = new AtomicInteger();
        final ThreadFactory factory = runnable -> {
            final var thread = new Thread(runnable, "server-fetch-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        final var executor = new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(10_000),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;

        // --- Java 21: заменить всё тело метода на ---
        // return Executors.newVirtualThreadPerTaskExecutor();
    }
}
