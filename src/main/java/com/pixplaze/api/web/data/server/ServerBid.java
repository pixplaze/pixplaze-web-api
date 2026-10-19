package com.pixplaze.api.web.data.server;

import java.time.OffsetDateTime;

/**
 * Заявка — сервер вместе с кодом регистрации из {@code minecraft_server_bid}: имя, владелец и статус — из сервера,
 * игровой адрес — его запись HOST ({@code null}, если адрес освобождён), код — ваучер этой строки связи.
 */
public record ServerBid(
        Long serverId,
        String name,
        MinecraftServerRegistrationStatus status,
        Long ownerProfileId,
        String ownerName,
        String host,
        Integer port,
        Long voucherCodeId,
        String code,
        OffsetDateTime createdAt
) {
}
