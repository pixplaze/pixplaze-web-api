package com.pixplaze.api.web.exception;

import java.util.UUID;

public class MinecraftPlayerAlreadyOwnedException extends RuntimeException {
    public MinecraftPlayerAlreadyOwnedException(String message) {
        super(message);
    }

    public MinecraftPlayerAlreadyOwnedException(UUID uuid) {
        super("Minecraft player with uuid '%s' is already owned by another profile!".formatted(uuid));
    }
}
