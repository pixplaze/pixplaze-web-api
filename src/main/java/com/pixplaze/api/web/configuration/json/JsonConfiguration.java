package com.pixplaze.api.web.configuration.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pixplaze.api.web.data.server.RawMinecraftServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JsonConfiguration {
    private static final SimpleModule MINECRAFT_NATIVE_JSON_MODULE = new SimpleModule("MINECRAFT_NATIVE_JSON_MODULE")
            .addDeserializer(RawMinecraftServer.class, new MinecraftNativeJsonDeserializer());

    @Bean
    @Primary
    @Profile("dev")
    public JsonMapper devSerializer() {
        return JsonMapper.builder()
                .addModule(MINECRAFT_NATIVE_JSON_MODULE)
                .findAndAddModules()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
    }

    @Bean
    @Primary
    @Profile("prod")
    public JsonMapper prodSerializer() {
        return JsonMapper.builder()
                .addModule(MINECRAFT_NATIVE_JSON_MODULE)
                .findAndAddModules()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();
    }
}
