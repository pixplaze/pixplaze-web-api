package com.pixplaze.api.web.repository;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerPortsInfo;
import com.pixplaze.api.web.data.db.tables.MinecraftServerTable;
import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@AllArgsConstructor
public class MinecraftServerRepository {

    private final JdbcClient jdbcClient;
    private final DSLContext dslContext;

    private static final List<MinecraftServerInfo> PIXPLAZE_SERVER_INFO_LIST = List.of(
            new MinecraftServerInfo(
                    "localhost",
                    new MinecraftServerPortsInfo(25565)
            ),
            new MinecraftServerInfo(
                    "185.23.80.106",
                    new MinecraftServerPortsInfo(50010, 50011, 50012)
            ),
            new MinecraftServerInfo(
                    "mc.pixplaze.net",
                    new MinecraftServerPortsInfo(50010, 50011, 50012)
            ),
            new MinecraftServerInfo(
                    "mc.hypixel.net",
                    new MinecraftServerPortsInfo(25565)
            ),
            new MinecraftServerInfo(
                    "mc.epserv.ru",
                    new MinecraftServerPortsInfo(25565)
            )
    );

    public List<MinecraftServerInfo> getPixplazeServerList() {
        return PIXPLAZE_SERVER_INFO_LIST;
    }

    public MinecraftServerInfo getPixplazeServerInfoById(int id) {
        try {
            return PIXPLAZE_SERVER_INFO_LIST.get(id);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public List<MinecraftServerInfo> getMinecraftServerList() {
        final var MINECRAFT_SERVER = MinecraftServerTable.MINECRAFT_SERVER;
        return dslContext.select()
                .from(MINECRAFT_SERVER)
                .fetchInto(MinecraftServerInfo.class);
//        return jdbcClient.sql("SELECT * FROM minecraft_server")
//                .query(new DataClassRowMapper<>(MinecraftServerInfo.class))
//                .list();
    }
}
