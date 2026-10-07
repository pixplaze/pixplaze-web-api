package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerBid;
import com.pixplaze.api.web.exception.http.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Создание сервера по заявке без плагина — после подтверждения кода в MOTD
 * ({@link com.pixplaze.api.web.service.server.MinecraftServerBidVerifier}) или админом без кода.
 * Заявку с плагином подтверждает сам плагин через device-flow
 * ({@link com.pixplaze.api.web.service.auth.device.MinecraftServerAuthorizationStrategy}).
 */
@Service
@RequiredArgsConstructor
public class MinecraftServerEnrollmentService {
    private final MinecraftServerBidService minecraftServerBidService;
    private final MinecraftServerService minecraftServerService;
    private final VoucherCodeService voucherCodeService;

    /// Подтверждает открытую NATIVE-заявку: создаёт сервер с игровым адресом из заявки и владельцем —
    /// профилем заявки, гасит код и закрывает заявку. Конфликт — заявка с плагином, уже закрыта
    /// (гонка проверки MOTD и админа) или её адрес успел занять другой сервер.
    @Transactional
    public MinecraftServer approveNative(MinecraftServerBid bid) {
        if (bid.getIntegration() != MinecraftServerStateInfo.IntegrationStatus.NATIVE) {
            throw new ConflictException("Bid #%d is confirmed by the plugin, not approved manually!".formatted(bid.getId()));
        }
        if (!minecraftServerBidService.approve(bid.getId())) {
            throw new ConflictException("Bid #%d is not pending!".formatted(bid.getId()));
        }

        final MinecraftServer server;
        try {
            server = minecraftServerService.create(
                    new MinecraftServer()
                            .setName(bid.getName())
                            .setOwnerProfileId(bid.getProfileId()),
                    MinecraftServerStateInfo.IntegrationStatus.NATIVE,
                    List.of(MinecraftServerHostInfo.server(bid.getHost(), bid.getPort()))
            );
        } catch (DuplicateKeyException e) {
            throw new ConflictException("A server at '%s:%s' is already registered!".formatted(bid.getHost(), bid.getPort()));
        }

        final var voucher = voucherCodeService.findById(bid.getVoucherCodeId())
                .orElseThrow(() -> new IllegalStateException("Bid #%d has no voucher code!".formatted(bid.getId())));
        voucherCodeService.activate(voucher, bid.getProfileId());

        return server;
    }
}
