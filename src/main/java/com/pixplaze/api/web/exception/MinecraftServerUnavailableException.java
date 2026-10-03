package com.pixplaze.api.web.exception;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.exception.http.ServiceUnavailableException;

/// Целевой MC-сервер недоступен/в обслуживании → 503 (наследует {@link ServiceUnavailableException}).
public class MinecraftServerUnavailableException extends ServiceUnavailableException {
  public MinecraftServerUnavailableException(String message) {
    super(message);
  }

  public MinecraftServerUnavailableException(MinecraftServerInfo minecraftServerInfo) {
    this("The Minecraft server at %s:%s is unavailable or in maintenance mode".formatted(
            minecraftServerInfo.host(),
            minecraftServerInfo.ports()
    ));
  }
}
