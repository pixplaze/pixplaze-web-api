package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.dto.MinecraftServerBidInfo;
import com.pixplaze.api.web.data.server.MinecraftServerBidStatus;
import com.pixplaze.api.web.exception.http.ConflictException;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.service.MinecraftServerAdminService;
import com.pixplaze.api.web.service.MinecraftServerBidService;
import com.pixplaze.api.web.service.MinecraftServerEnrollmentService;
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
    private final MinecraftServerAdminService minecraftServerAdminService;
    private final MinecraftServerBidService minecraftServerBidService;
    private final MinecraftServerEnrollmentService minecraftServerEnrollmentService;

    @PostMapping("/{id}/ban")
    public ResponseEntity<Void> ban(@PathVariable Long id) {
        minecraftServerAdminService.ban(id);
        return ResponseEntity.noContent().build();
    }

    /// Заявки на регистрацию серверов, новые первыми; без {@code status} — все.
    @GetMapping("/bids")
    public List<MinecraftServerBidInfo> getBids(@RequestParam(required = false) MinecraftServerBidStatus status) {
        return minecraftServerBidService.findInfos(status);
    }

    /// Подтверждение заявки без плагина без проверки кода в MOTD — для известных владельцев.
    @PostMapping("/bids/{id}/approve")
    public ResponseEntity<MinecraftServer> approveBid(@PathVariable Long id) {
        final var bid = minecraftServerBidService.findById(id)
                .orElseThrow(() -> new NotFoundException("Bid #%d not found!".formatted(id)));
        return ResponseEntity.status(HttpStatus.CREATED).body(minecraftServerEnrollmentService.approveNative(bid));
    }

    /// Отклонение открытой заявки любого типа: адрес освобождается.
    @PostMapping("/bids/{id}/reject")
    public ResponseEntity<Void> rejectBid(@PathVariable Long id) {
        minecraftServerBidService.findById(id)
                .orElseThrow(() -> new NotFoundException("Bid #%d not found!".formatted(id)));
        if (!minecraftServerBidService.reject(id)) {
            throw new ConflictException("Bid #%d is not pending!".formatted(id));
        }
        return ResponseEntity.noContent().build();
    }
}
