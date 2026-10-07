package com.pixplaze.api.web.data.server;

/// Статус заявки на регистрацию сервера: открытая (PENDING) ждёт регистрации плагином или
/// подтверждения кода в MOTD (NATIVE); закрытая адрес не блокирует. EXPIRED — истёк срок жизни.
public enum MinecraftServerBidStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED
}
