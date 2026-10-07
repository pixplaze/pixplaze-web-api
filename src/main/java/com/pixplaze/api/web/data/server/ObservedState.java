package com.pixplaze.api.web.data.server;

import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;

import java.time.Instant;

/**
 * Часть состояния сервера от одного источника (пинг или heartbeat плагина) вместе с моментом получения.
 * Время у каждой части своё: часть плагина гаснет по TTL, часть пинга — нет.
 *
 * @param state заполнены только поля своего источника
 * @param at    когда получено
 */
public record ObservedState(MinecraftServerStateInfo state, Instant at) {
}
