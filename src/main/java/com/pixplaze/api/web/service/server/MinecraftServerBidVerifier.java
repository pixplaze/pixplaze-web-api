package com.pixplaze.api.web.service.server;

import com.pixplaze.api.web.data.server.ServerBid;
import com.pixplaze.api.web.service.MinecraftServerBidService;
import com.pixplaze.api.web.service.MinecraftServerEnrollmentService;
import com.pixplaze.api.web.service.MinecraftServerMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

/**
 * Фоновое сопровождение заявок: закрывает просроченные (EXPIRED) и регистрирует заявки, чей код владелец
 * вставил в MOTD (с плагином или без — плагин подключится к уже зарегистрированному серверу тем же кодом). Пинг идёт на {@code serverFetchExecutor}, чтобы мёртвые серверы
 * не держали общий поток планировщика. Заявка, не прошедшая проверку, ждёт следующего цикла или истечения срока.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinecraftServerBidVerifier {

    /// Коды форматирования Minecraft ({@code §a}, {@code &l}, …): владелец может раскрасить код в MOTD.
    private static final Pattern FORMATTING_CODES = Pattern.compile("[§&][0-9a-fk-orA-FK-OR]");

    private final MinecraftServerBidService minecraftServerBidService;
    private final MinecraftServerEnrollmentService minecraftServerEnrollmentService;
    private final MinecraftServerMonitoringService monitoringService;
    private final ExecutorService serverFetchExecutor;

    @Scheduled(
            fixedDelayString = "${app.servers.bids.verify-millis:300000}",
            initialDelayString = "${app.servers.bids.verify-initial-millis:60000}"
    )
    public void verifyPending() {
        try {
            final var expired = minecraftServerBidService.expireOverdue();
            if (expired > 0) {
                log.info("Server bids expired: {}", expired);
            }

            for (final var bid : minecraftServerBidService.findPending()) {
                if (bid.host() != null) {
                    verifyAsync(bid);
                }
            }
        } catch (Exception e) {
            log.warn("Server bid verification failed: {}", e.toString());
        }
    }

    private void verifyAsync(ServerBid bid) {
        CompletableFuture.supplyAsync(() -> pingMotd(bid), serverFetchExecutor)
                .thenAccept(motd -> {
                    if (containsCode(motd, bid.code())) {
                        approve(bid);
                    }
                })
                .exceptionally(error -> {
                    log.debug("Bid #{} at {}:{} not verified — {}", bid.serverId(), bid.host(), bid.port(), error.toString());
                    return null;
                });
    }

    private String pingMotd(ServerBid bid) {
        try {
            return monitoringService.ping(bid.host(), bid.port()).motd();
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private void approve(ServerBid bid) {
        try {
            minecraftServerEnrollmentService.enrollWithNativeIntegration(bid.serverId());
            log.info("Bid #{} confirmed by MOTD: server registered at {}:{}", bid.serverId(), bid.host(), bid.port());
        } catch (Exception e) {
            log.warn("Bid #{} confirmed by MOTD but not registered: {}", bid.serverId(), e.getMessage());
        }
    }

    /// Код ищется в MOTD без учёта регистра и кодов форматирования.
    static boolean containsCode(String motd, String code) {
        if (motd == null || code == null) {
            return false;
        }

        final var plain = FORMATTING_CODES.matcher(motd).replaceAll("");
        return plain.toUpperCase(Locale.ROOT).contains(code.toUpperCase(Locale.ROOT));
    }
}
