package com.pixplaze.api.web.configuration;

import lombok.Getter;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@Getter
public class ApplicationConfiguration {
    private final boolean development;

    public ApplicationConfiguration(Environment environment) {
        this.development = environment.matchesProfiles("dev"); // Spring 6.1+
    }
}