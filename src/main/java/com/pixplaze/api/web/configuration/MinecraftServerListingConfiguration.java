package com.pixplaze.api.web.configuration;

import com.pixplaze.api.web.configuration.properties.MinecraftServerListingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinecraftServerListingProperties.class)
public class MinecraftServerListingConfiguration {
}
