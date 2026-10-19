package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.server.MinecraftServerHeartbeatInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerHeartbeatResponse;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.dto.MinecraftServerBidInfo;
import com.pixplaze.api.web.data.dto.MinecraftServerBidRequest;
import com.pixplaze.api.web.data.dto.MinecraftServerBidResponse;
import com.pixplaze.api.web.data.dto.PluginAccessTokenResponse;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.data.user.MinecraftServerPrincipal;
import com.pixplaze.api.web.service.MinecraftServerBidService;
import com.pixplaze.api.web.service.MinecraftServerService;
import com.pixplaze.api.web.service.ServerAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    private final ServerAccessService serverAccessService;

    @Autowired
    public MinecraftServerController(
            MinecraftServerService minecraftServerService,
            MinecraftServerBidService minecraftServerBidService,
            ServerAccessService serverAccessService
    ) {
        this.minecraftServerService = minecraftServerService;
        this.minecraftServerBidService = minecraftServerBidService;
        this.serverAccessService = serverAccessService;
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

    /// Освежение состояния: веб-апп шлёт id видимых серверов → их {@link MinecraftServerStateInfo} из снапшота
    /// (без блокирующего on-demand fetch), каждое со своим {@code minecraftServerId}. Неизвестные id опускаются.
    @PreAuthorize("permitAll()")
    @PostMapping("/state")
    public List<MinecraftServerStateInfo> getServersState(@RequestBody List<Long> serverIds) {
        return minecraftServerService.getServerStates(serverIds);
    }

    /// Heartbeat плагина ({@link MinecraftServerHeartbeatInfo}) под MAD-токеном сервера. Identity сервера —
    /// из токена, не из тела; из тела берётся только то, за что отвечает плагин (состояние, плагины, лицензия).
    @PreAuthorize("hasRole('MINECRAFT_SERVER')")
    @PostMapping("/heartbeat")
    public MinecraftServerHeartbeatResponse heartbeat(
            @AuthenticationPrincipal MinecraftServerPrincipal principal,
            @RequestBody MinecraftServerHeartbeatInfo heartbeat
    ) {
        return minecraftServerService.handleHeartbeat(principal.getMinecraftServerId(), heartbeat);
    }

    /// Обмен токена профиля на токен доступа к плагину этого сервера (консоль, чат, REST плагина).
    /// 403 — у профиля на сервере нет ни одной роли: экран сервера фронт не показывает.
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/token")
    public PluginAccessTokenResponse exchangePluginToken(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @PathVariable Long id
    ) {
        return serverAccessService.issuePluginToken(principal.getId(), id);
    }

    /// Игрок оценивает сервер (1..5). Identity голосующего — из токена, не из тела: один голос
    /// на игрока (повторный вызов переголосовывает). Рейтинг персистится в БД. Оценивать может только
    /// профиль с привязанным игроком.
    @PreAuthorize("hasRole('USER') and @profileAccess.hasLinkedPlayer(principal)")
    @PostMapping("/rate/{serverId}")
    public void rate(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @PathVariable Long serverId,
            @RequestParam @Min(1) @Max(5) int rating
    ) {
        minecraftServerService.rate(serverId, principal.getId(), rating);
    }

    /// Заявка владельца на регистрацию сервера: код заявки и превью сервера (см. {@link MinecraftServerBidService#createBid}).
    @PostMapping("/bids")
    public ResponseEntity<MinecraftServerBidResponse> createBid(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @RequestBody @Valid MinecraftServerBidRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(minecraftServerBidService.createBid(request, principal.getId()));
    }

    /// Заявки текущего пользователя, новые первыми: статус, код и срок открытых.
    @GetMapping("/bids")
    public List<MinecraftServerBidInfo> getMyBids(@AuthenticationPrincipal ApplicationClientPrincipal principal) {
        return minecraftServerBidService.findInfosOf(principal.getId());
    }

    @PostMapping("/favorite")
    public void addFavorite(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @RequestParam List<Long> serverIds
    ) {
        minecraftServerService.addFavorite(serverIds, principal.getId());
    }

    @DeleteMapping("/favorite")
    public void removeFavorite(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @RequestParam List<Long> serverIds
    ) {
        minecraftServerService.removeFavorite(serverIds, principal.getId());
    }

    @GetMapping("/favorite")
    public List<MinecraftServerInfo> getFavorite(@AuthenticationPrincipal ApplicationClientPrincipal applicationClientPrincipal) {
        return minecraftServerService.getFavorite(applicationClientPrincipal.getId());
    }
}
