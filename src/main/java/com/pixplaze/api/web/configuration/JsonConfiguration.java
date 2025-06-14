package com.pixplaze.api.web.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class JsonConfiguration {
    @Profile("dev")
    @Bean
    public ObjectMapper devObjectMapper() {
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
    }

    @Profile("prod")
    @Bean
    public ObjectMapper prodObjectMapper() {
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
    }
}
