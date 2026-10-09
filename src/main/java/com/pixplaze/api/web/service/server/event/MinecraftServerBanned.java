package com.pixplaze.api.web.service.server.event;

/// Сервер забанен (переход, а не повторный бан). Публикуется внутри транзакции бана: синхронные
/// слушатели (отзыв refresh-токенов) выполняются в ней же и откатываются вместе с баном.
public record MinecraftServerBanned(long minecraftServerId) {
}
