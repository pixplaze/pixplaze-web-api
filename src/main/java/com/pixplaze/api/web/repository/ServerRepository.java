package com.pixplaze.api.web.repository;

import com.pixplaze.api.ext.data.server.PixplazeServerInfo;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ServerRepository {
    private static final List<PixplazeServerInfo> PIXPLAZE_SERVER_INFO_LIST = List.of(
            new PixplazeServerInfo(
                    "localhost",
                    25566,
                    null
            )
    );

    public List<PixplazeServerInfo> getPixplazeServerList() {
        return PIXPLAZE_SERVER_INFO_LIST;
    }

    public PixplazeServerInfo getPixplazeServerInfoById(int id) {
        try {
            return PIXPLAZE_SERVER_INFO_LIST.get(id);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }
}
