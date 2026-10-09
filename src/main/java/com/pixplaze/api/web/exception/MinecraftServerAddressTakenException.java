package com.pixplaze.api.web.exception;

import com.pixplaze.api.web.exception.http.ConflictException;

/// Игровой адрес уже занят другим сервером → 409 (наследует {@link ConflictException}). Отдельный тип
/// нужен device-flow: занятый адрес там — {@code invalid_request}, а прочие конфликты — {@code access_denied}.
public class MinecraftServerAddressTakenException extends ConflictException {
    public MinecraftServerAddressTakenException(String host, Integer port) {
        super("A server at '%s:%s' is already registered!".formatted(host, port));
    }
}
