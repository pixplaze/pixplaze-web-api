package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.dto.PrimaryPlayerRequest;
import com.pixplaze.api.web.data.dto.ProfilePlayerInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.exception.http.ForbiddenException;
import com.pixplaze.api.web.service.ProfilePlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/// Игроки своего профиля: серверы, основной игрок на сервере, отвязка.
@RestController
@RequiredArgsConstructor
@Tag(name = "Игроки профиля")
@RequestMapping("/profiles/{profileId}/players")
@PreAuthorize("hasRole('USER')")
public class ProfilePlayerController {

    private final ProfilePlayerService profilePlayerService;

    @Operation(summary = "Игроки профиля с серверами и признаком основного")
    @GetMapping
    public List<ProfilePlayerInfo> getPlayers(
            @PathVariable Long profileId,
            @AuthenticationPrincipal ApplicationClientPrincipal principal
    ) {
        return profilePlayerService.findPlayers(requireOwn(profileId, principal));
    }

    @Operation(summary = "Сделать игрока основным на сервере")
    @PutMapping("/{uuid}/primary")
    public ResponseEntity<Void> setPrimary(
            @PathVariable Long profileId,
            @PathVariable UUID uuid,
            @RequestBody @Valid PrimaryPlayerRequest request,
            @AuthenticationPrincipal ApplicationClientPrincipal principal
    ) {
        profilePlayerService.setPrimary(requireOwn(profileId, principal), uuid, request.minecraftServerId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Отвязать игрока от профиля")
    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> unlink(
            @PathVariable Long profileId,
            @PathVariable UUID uuid,
            @AuthenticationPrincipal ApplicationClientPrincipal principal
    ) {
        profilePlayerService.unlink(requireOwn(profileId, principal), uuid);
        return ResponseEntity.noContent().build();
    }

    private static Long requireOwn(Long profileId, ApplicationClientPrincipal principal) {
        if (!profileId.equals(principal.getId())) {
            throw new ForbiddenException("Only own players can be managed");
        }
        return profileId;
    }
}
