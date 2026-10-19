package com.pixplaze.api.web.configuration.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceAuthorizationPropertiesTest {

    private static DeviceAuthorizationProperties of(String uri) {
        return new DeviceAuthorizationProperties(Duration.ofMinutes(5), Duration.ofSeconds(5), Duration.ofSeconds(15), uri);
    }

    @Test
    void addsHttpsToAddressWithoutScheme() {
        assertEquals("https://pixplaze.net/auth?userCode=A", of("pixplaze.net/auth").verificationUriComplete("A"));
    }

    @Test
    void addsHttpToLocalAddress() {
        assertEquals("http://localhost:5173/auth", of("localhost:5173/auth").verificationUri());
    }

    @Test
    void keepsExplicitScheme() {
        assertEquals("http://pixplaze.test/auth", of("http://pixplaze.test/auth").verificationUri());
    }
}
