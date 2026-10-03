package com.pixplaze.api.web.data.dto;

public record CreateMinecraftServerRequest(
        String name,
        String host,
        Integer port,
        Boolean integration
) {}
