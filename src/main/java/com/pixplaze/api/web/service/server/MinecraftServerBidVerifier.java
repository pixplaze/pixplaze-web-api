package com.pixplaze.api.web.service.server;

import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerBid;
import com.pixplaze.api.web.data.db.tables.pojos.VoucherCode;
import com.pixplaze.api.web.service.MinecraftServerBidService;
import com.pixplaze.api.web.service.MinecraftServerEnrollmentService;
import com.pixplaze.api.web.service.MinecraftServerMonitoringService;
import com.pixplaze.api.web.service.VoucherCodeService;
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
 * Фоновое сопровождение заявок: закрывает просроченные (EXPIRED) и подтверждает заявки без плагина,
 * чей код владелец вставил в MOTD. Пинг идёт на {@code serverFetchExecutor}, чтобы мёртвые серверы
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
    private final VoucherCodeService voucherCodeService;
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

            for (final var bid : minecraftServerBidService.findPending(MinecraftServerStateInfo.IntegrationStatus.NATIVE)) {
                voucherCodeService.findById(bid.getVoucherCodeId())
                        .map(VoucherCode::getCode)
                        .ifPresent(code -> verifyAsync(bid, code));
            }
        } catch (Exception e) {
            log.warn("Server bid verification failed: {}", e.toString());
        }
    }

    private void verifyAsync(MinecraftServerBid bid, String code) {
        CompletableFuture.supplyAsync(() -> pingMotd(bid), serverFetchExecutor)
                .thenAccept(motd -> {
                    if (containsCode(motd, code)) {
                        approve(bid);
                    }
                })
                .exceptionally(error -> {
                    log.debug("Bid #{} at {}:{} not verified — {}", bid.getId(), bid.getHost(), bid.getPort(), error.toString());
                    return null;
                });
    }

    private String pingMotd(MinecraftServerBid bid) {
        try {
            return monitoringService.ping(bid.getHost(), bid.getPort()).motd();
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private void approve(MinecraftServerBid bid) {
        try {
            final var server = minecraftServerEnrollmentService.approveNative(bid);
            log.info("Bid #{} confirmed by MOTD: server #{} at {}:{}", bid.getId(), server.getId(), bid.getHost(), bid.getPort());
        } catch (Exception e) {
            log.warn("Bid #{} confirmed by MOTD but not approved: {}", bid.getId(), e.getMessage());
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
