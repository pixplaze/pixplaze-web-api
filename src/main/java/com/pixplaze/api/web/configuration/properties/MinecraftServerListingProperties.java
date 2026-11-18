package com.pixplaze.api.web.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.servers.listing")
public record MinecraftServerListingProperties(
        Duration pingInterval,
        Duration pingInitialDelay,
        Duration syncInterval,
        Duration syncInitialDelay
) {}
