package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.data.dto.MinecraftServerBidInfo;
import com.pixplaze.api.web.data.server.MinecraftServerRegistrationStatus;
import com.pixplaze.api.web.exception.http.ConflictException;
import com.pixplaze.api.web.service.MinecraftServerBidService;
import com.pixplaze.api.web.service.MinecraftServerEnrollmentService;
import com.pixplaze.api.web.service.MinecraftServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Админские операции над серверами (маршрут /admin/** защищён ролью ADMIN в SecurityConfiguration).
@RestController
@RequestMapping("/admin/servers")
@RequiredArgsConstructor
public class MinecraftServerAdminController {
    private final MinecraftServerService minecraftServerService;
    private final MinecraftServerBidService minecraftServerBidService;
    private final MinecraftServerEnrollmentService minecraftServerEnrollmentService;

    @PostMapping("/{id}/ban")
    public ResponseEntity<Void> ban(@PathVariable Long id) {
        minecraftServerService.ban(id);
        return ResponseEntity.noContent().build();
    }

    /// Заявки на регистрацию серверов (серверы с кодом), новые первыми; без {@code status} — все.
    @GetMapping("/bids")
    public List<MinecraftServerBidInfo> getBids(@RequestParam(required = false) MinecraftServerRegistrationStatus status) {
        return minecraftServerBidService.findInfos(status);
    }

    /// Регистрация заявки без проверки кода в MOTD — для известных владельцев; {@code id} — id сервера.
    @PostMapping("/bids/{id}/approve")
    public ResponseEntity<MinecraftServerInfo> approveBid(@PathVariable Long id) {
        final var server = minecraftServerEnrollmentService.enrollWithNativeIntegration(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(minecraftServerService.getServerInfo(server.getId()));
    }

    /// Отклонение открытой заявки: сервер остаётся в истории как REJECTED, адрес свободен для новых заявок.
    @PostMapping("/bids/{id}/reject")
    public ResponseEntity<Void> rejectBid(@PathVariable Long id) {
        if (!minecraftServerBidService.reject(id)) {
            throw new ConflictException("Bid #%d is not pending!".formatted(id));
        }
        return ResponseEntity.noContent().build();
    }
}
