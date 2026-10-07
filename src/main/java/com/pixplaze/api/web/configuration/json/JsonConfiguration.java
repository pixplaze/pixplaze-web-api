package com.pixplaze.api.web.configuration.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JsonConfiguration {

    /// Без профиля работает автонастроенный JsonMapper Spring Boot — миксины подключаем и к нему.
    @Bean
    public JsonMapperBuilderCustomizer oauthJsonMixins() {
        return OAuthJsonMixin::register;
    }

    @Bean
    @Primary
    @Profile("dev")
    public JsonMapper devSerializer() {
        return OAuthJsonMixin.register(JsonMapper.builder())
                .findAndAddModules()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
    }

    @Bean
    @Primary
    @Profile("prod")
    public JsonMapper prodSerializer() {
        return OAuthJsonMixin.register(JsonMapper.builder())
                .findAndAddModules()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
    }
}
