package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.data.dto.MinecraftServerBidRequest;
import com.pixplaze.api.web.data.dto.MinecraftServerBidResponse;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.service.MinecraftServerBidService;
import com.pixplaze.api.web.service.MinecraftServerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/servers")
public class MinecraftServerController {
    private final MinecraftServerService minecraftServerService;
    private final MinecraftServerBidService minecraftServerBidService;

    @Autowired
    public MinecraftServerController(
            MinecraftServerService minecraftServerService,
            MinecraftServerBidService minecraftServerBidService
    ) {
        this.minecraftServerService = minecraftServerService;
        this.minecraftServerBidService = minecraftServerBidService;
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

    /// Заявка владельца на регистрацию сервера: создаёт заявку и возвращает код для конфига сервера.
    @PostMapping("/bids")
    public ResponseEntity<MinecraftServerBidResponse> createBid(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @RequestBody @Valid MinecraftServerBidRequest request
    ) {
        final var result = minecraftServerBidService.createBid(
                request.name(), request.host(), request.ownerUsername(), principal.getId()
        );
        final var bid = result.bid();
        final var body = new MinecraftServerBidResponse(
                bid.getId(), bid.getName(), bid.getHost(), bid.getOwnerUsername(), result.code()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
