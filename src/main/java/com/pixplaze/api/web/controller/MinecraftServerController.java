package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.service.MinecraftServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/servers")
public class MinecraftServerController {
    private final MinecraftServerService minecraftServerService;

    @Autowired
    public MinecraftServerController(MinecraftServerService minecraftServerService) {
        this.minecraftServerService = minecraftServerService;
    }

    @GetMapping
    public List<MinecraftServerInfo> getPixplazeServers(
            @RequestParam(value = "thumbnail", defaultValue = "false") Boolean fetchThumbnail
    ) {
        return minecraftServerService.getPixplazeServerList(fetchThumbnail);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MinecraftServerInfo> getPixplazeServer(
            @PathVariable Integer id,
            @RequestParam(value = "thumbnail", defaultValue = "false") Boolean fetchThumbnail
    ) {
        return ResponseEntity.ofNullable(minecraftServerService.getServerInfo(id, fetchThumbnail));
    }
}
