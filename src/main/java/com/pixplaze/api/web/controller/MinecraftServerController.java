package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.dto.MinecraftServerBidRequest;
import com.pixplaze.api.web.data.dto.MinecraftServerBidResponse;
import com.pixplaze.api.web.data.dto.MinecraftServerHeartbeatRequest;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.data.user.MinecraftPlayerPrincipal;
import com.pixplaze.api.web.data.user.MinecraftServerPrincipal;
import com.pixplaze.api.web.service.MinecraftServerBidService;
import com.pixplaze.api.web.service.MinecraftServerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /// Публичный листинг серверов из материализованного снапшота (пагинация; тир зависит от online/plugin).
    @PreAuthorize("permitAll()")
    @GetMapping
    public List<MinecraftServerInfo> getServers(
            @RequestParam(required = false) String search,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset
    ) {
        return minecraftServerService.listServers(search, limit, offset);
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<MinecraftServerInfo> getServer(
            @PathVariable Long id
    ) {
        return ResponseEntity.ofNullable(minecraftServerService.getServerInfo(id));
    }

    /// Освежение online-данных: веб-апп шлёт id видимых серверов → только их {@link MinecraftServerStateInfo}
    /// из снапшота (без блокирующего on-demand fetch). Неизвестные id опускаются.
    @PreAuthorize("permitAll()")
    @PostMapping("/state")
    public Map<Long, MinecraftServerStateInfo> getServersState(@RequestBody List<Long> serverIds) {
        return minecraftServerService.getServerStates(serverIds);
    }

    /// Tier-3 push: сервер с нашим плагином присылает своё состояние под MAD-токеном. Identity сервера —
    /// из токена, не из тела. Кладётся в снапшот и отдаётся в листинге до протухания (TTL).
    @PreAuthorize("hasRole('MINECRAFT_SERVER')")
    @PostMapping("/heartbeat")
    public void heartbeat(
            @AuthenticationPrincipal MinecraftServerPrincipal principal,
            @RequestBody MinecraftServerHeartbeatRequest request
    ) {
        minecraftServerService.handleHeartbeat(principal.getServerId(), request);
    }

    /// Игрок оценивает сервер (1..5). Identity голосующего — из токена, не из тела: один голос
    /// на игрока (повторный вызов переголосовывает). Рейтинг персистится в БД.
    @PreAuthorize("hasRole('MINECRAFT_PLAYER')")
    @PostMapping("/rate/{serverId}")
    public void rate(
            @AuthenticationPrincipal MinecraftPlayerPrincipal principal,
            @PathVariable Long serverId,
            @RequestParam int rating
    ) {
        minecraftServerService.rate(serverId, principal.getUuid(), rating);
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

    @PostMapping("/favorite")
    public void addFavorite(@RequestParam List<Long> serverIds) {

    }

    @GetMapping("/favorite")
    public ResponseEntity<List<MinecraftServer>> getFavorite(@AuthenticationPrincipal ApplicationClientPrincipal applicationClientPrincipal) {
        return ResponseEntity.ok(minecraftServerService.getFavorite(applicationClientPrincipal.getId()));
    }
}
