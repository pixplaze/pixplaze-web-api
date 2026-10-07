package com.pixplaze.api.web.service.server;

import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo.IntegrationStatus;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MinecraftServerStatesTest {

    private static final MinecraftServerStateInfo PING = MinecraftServerStates.ping(120L, 5, 50);
    private static final MinecraftServerStateInfo PLUGIN = MinecraftServerStateInfo.heartbeat(19.9, 35L, 3_600_000L, "NORMAL", 7, 60);

    @Test
    void withoutPluginEverythingComesFromPing() {
        final var merged = MinecraftServerStates.merge(PING, null, IntegrationStatus.NATIVE);

        assertEquals(Status.ONLINE, merged.status());
        assertEquals(120L, merged.ping());
        assertEquals(5, merged.players().online());
        assertEquals(IntegrationStatus.NATIVE, merged.integrationStatus());
        assertNull(merged.tps());
        assertNull(merged.uptime());
    }

    @Test
    void freshPluginWinsOverlappingFields() {
        final var merged = MinecraftServerStates.merge(PING, PLUGIN, IntegrationStatus.NATIVE);

        assertEquals(35L, merged.ping(), "медианный пинг игроков важнее задержки web-api");
        assertEquals(7, merged.players().online());
        assertEquals(60, merged.players().max());
        assertEquals(19.9, merged.tps());
        assertEquals("NORMAL", merged.difficulty());
        assertEquals(IntegrationStatus.PLUGIN, merged.integrationStatus());
    }

    @Test
    void livePluginKeepsServerOnlineWhenPingFails() {
        final var merged = MinecraftServerStates.merge(MinecraftServerStates.offline(), PLUGIN, IntegrationStatus.PLUGIN);

        assertEquals(Status.ONLINE, merged.status());
    }

    @Test
    void notPingedYetIsOffline() {
        final var merged = MinecraftServerStates.merge(null, null, IntegrationStatus.NATIVE);

        assertEquals(Status.OFFLINE, merged.status());
        assertNull(merged.ping());
    }
}
