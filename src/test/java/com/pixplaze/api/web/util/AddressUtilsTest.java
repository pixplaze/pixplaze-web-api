package com.pixplaze.api.web.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressUtilsTest {

    @Test
    void recognizesLoopbackAddresses() {
        assertTrue(AddressUtils.isLoopback("localhost"));
        assertTrue(AddressUtils.isLoopback(" LocalHost "));
        assertTrue(AddressUtils.isLoopback("127.0.0.1"));
        assertTrue(AddressUtils.isLoopback("127.1.2.3"));
        assertTrue(AddressUtils.isLoopback("::1"));
    }

    @Test
    void publicAddressesAreNotLoopback() {
        assertFalse(AddressUtils.isLoopback("mc.pixplaze.net"));
        assertFalse(AddressUtils.isLoopback("127.example.net"));
        assertFalse(AddressUtils.isLoopback("185.23.80.106"));
        assertFalse(AddressUtils.isLoopback(null));
    }
}
