package com.pixplaze.api.web.exception;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class MinecraftServerIsUnavailableException extends RuntimeException {
  public MinecraftServerIsUnavailableException(String message) {
    super(message);
  }

  public MinecraftServerIsUnavailableException(MinecraftServerInfo minecraftServerInfo) {
    this("The Minecraft server at %s:%s is unavailable or in maintenance mode".formatted(
            minecraftServerInfo.host(),
            minecraftServerInfo.ports()
    ));
  }
}
