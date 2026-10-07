package com.pixplaze.api.web.service.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftServerBidVerifierTest {

    @Test
    void findsCodeInPlainMotd() {
        assertTrue(MinecraftServerBidVerifier.containsCode("Welcome! A1B2C3D4", "A1B2C3D4"));
    }

    @Test
    void ignoresCase() {
        assertTrue(MinecraftServerBidVerifier.containsCode("code: a1b2c3d4", "A1B2C3D4"));
    }

    @Test
    void ignoresFormattingCodesInsideCode() {
        assertTrue(MinecraftServerBidVerifier.containsCode("§aA1B2§lC3D4§r", "A1B2C3D4"));
        assertTrue(MinecraftServerBidVerifier.containsCode("&6A1&eB2C3D4", "A1B2C3D4"));
    }

    @Test
    void rejectsMissingOrPartialCode() {
        assertFalse(MinecraftServerBidVerifier.containsCode("A1B2C3", "A1B2C3D4"));
        assertFalse(MinecraftServerBidVerifier.containsCode(null, "A1B2C3D4"));
        assertFalse(MinecraftServerBidVerifier.containsCode("A1B2C3D4", null));
    }
}
