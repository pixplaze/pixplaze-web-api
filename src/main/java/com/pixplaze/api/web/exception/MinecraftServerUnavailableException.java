package com.pixplaze.api.web.exception;

import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.exception.http.ServiceUnavailableException;
import com.pixplaze.api.web.util.AddressUtils;

/// Целевой MC-сервер недоступен/в обслуживании → 503 (наследует {@link ServiceUnavailableException}).
public class MinecraftServerUnavailableException extends ServiceUnavailableException {
  public MinecraftServerUnavailableException(String message) {
    super(message);
  }

  public MinecraftServerUnavailableException(MinecraftServerInfo minecraftServerInfo) {
    this("The Minecraft server at %s is unavailable or in maintenance mode".formatted(
            minecraftServerInfo.host(MinecraftServerHostInfo.Type.HOST)
                    .map(host -> AddressUtils.hostAndPort(host.address(), host.port()))
                    .orElse("#" + minecraftServerInfo.id())
    ));
  }
}
