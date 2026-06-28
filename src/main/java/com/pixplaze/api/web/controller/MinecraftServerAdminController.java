package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.service.MinecraftServerAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// Админские операции над серверами (маршрут /admin/** защищён ролью ADMIN в SecurityConfiguration).
@RestController
@RequestMapping("/admin/servers")
@RequiredArgsConstructor
public class MinecraftServerAdminController {
    private final MinecraftServerAdminService minecraftServerAdminService;

    @PostMapping("/{id}/ban")
    public ResponseEntity<Void> ban(@PathVariable Long id) {
        minecraftServerAdminService.ban(id);
        return ResponseEntity.noContent().build();
    }
}
