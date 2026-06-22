package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.server.MinecraftServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class MinecraftServerPollingService {
    private static final int POLLING_BATCH_SIZE = 100;
    private static final long POLLING_RATE_MILLIS = 60_000;
    private static final int MAX_CONCURRENCY = 500; // важно!

    private final ExecutorService pollingExecutor = new ThreadPoolExecutor(
            MAX_CONCURRENCY,
            MAX_CONCURRENCY,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(200_000),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final MinecraftServerMonitoringService minecraftServerMonitoringService;
    private final Map<String, MinecraftServer> cache = new ConcurrentHashMap<>();
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private volatile List<InetSocketAddress> addresses = List.of();

    @Autowired
    public MinecraftServerPollingService(MinecraftServerMonitoringService service) {
        this.minecraftServerMonitoringService = service;
    }

    @Scheduled(fixedDelay = POLLING_RATE_MILLIS)
    public void poll() {
        CompletableFuture.runAsync(
                this::pollMinecraftServers,
                CompletableFuture.delayedExecutor(generateJitterMillis(), TimeUnit.MILLISECONDS)
        );
    }

    /// Updates polling list
    public void push(List<InetSocketAddress> newList) {
        this.addresses = List.copyOf(newList); // immutable snapshot
    }

    public Collection<MinecraftServer> get() {
        return cache.values();
    }

    public MinecraftServer get(String host) {
        return cache.get(host);
    }

    private void pollMinecraftServers() {
        log.debug("Polling Minecraft servers...");
        List<InetSocketAddress> snapshot = this.addresses;

        int total = snapshot.size();
        for (int i = 0; i < total; i += POLLING_BATCH_SIZE) {
            int end = Math.min(i + POLLING_BATCH_SIZE, total);
            List<InetSocketAddress> batch = snapshot.subList(i, end);

            pollBatch(batch);
        }
    }

    private void pollBatch(List<InetSocketAddress> batch) {
        List<CompletableFuture<MinecraftServer>> futures = new ArrayList<>(batch.size());

        for (InetSocketAddress address : batch) {
            futures.add(pollOne(address));
        }

        // Ждём завершения ТОЛЬКО текущего батча
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private CompletableFuture<MinecraftServer> pollOne(InetSocketAddress address) {
        return CompletableFuture.supplyAsync(() -> fetch(address), pollingExecutor)
                .whenComplete((server, exception) -> {
                    if (server != null) {
                        cache(server);
                    }
                    if (exception != null) {
                        System.out.printf("%s: %s%n", address.getHostString(), exception); // TODO: логировать через логгер
                    }
                });
    }

    private MinecraftServer fetch(InetSocketAddress address) {
        try {
            return minecraftServerMonitoringService.getServer(address);
        } catch (IOException e) {
            System.out.printf("%s: %s%n", address.getHostString(), e);
            return null; // важно: не роняем future
        }
    }

    private void cache(MinecraftServer server) {
        cache.merge(
                server.getHost(),
                server,
                this::merge
        );
    }

    public MinecraftServer merge(MinecraftServer cached, MinecraftServer retrieved) {
        cached.setMotd(retrieved.getMotd());
        cached.setFavicon(retrieved.getFavicon());
        cached.setCore(retrieved.getCore());
        cached.setState(retrieved.getState());
        return cached;
    }

    private long generateJitterMillis() {
        return (long) (POLLING_RATE_MILLIS * random.nextDouble(0.0, 0.2));
    }
}