package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.server.PixplazeServerInfo;
import com.pixplaze.api.web.service.ServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/servers")
public class ServerController {
    private final ServerService serverService;

    @Autowired
    public ServerController(ServerService serverService) {
        this.serverService = serverService;
    }

    @GetMapping
    public List<PixplazeServerInfo> getPixplazeServers(
            @RequestParam(value = "thumbnail", defaultValue = "false") Boolean fetchThumbnail
    ) {
        return serverService.getPixplazeServerList(fetchThumbnail);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PixplazeServerInfo> getPixplazeServer(
            @PathVariable Integer id,
            @RequestParam(value = "thumbnail", defaultValue = "false") Boolean fetchThumbnail
    ) {
        return ResponseEntity.ofNullable(serverService.getServerInfo(id, fetchThumbnail));
    }
}
