package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.dto.FriendAddRequest;
import com.pixplaze.api.web.data.dto.FriendInfo;
import com.pixplaze.api.web.data.dto.FriendOnlineInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.service.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/// Друзья текущего профиля и их онлайн.
@RestController
@RequiredArgsConstructor
@Tag(name = "Друзья")
@RequestMapping("/friends")
@PreAuthorize("hasRole('USER')")
public class FriendController {

    private final FriendService friendService;

    @Operation(summary = "Друзья со статусом: MUTUAL, OUTGOING, INCOMING")
    @GetMapping
    public List<FriendInfo> getFriends(@AuthenticationPrincipal ApplicationClientPrincipal principal) {
        return friendService.findFriends(principal.getId());
    }

    @Operation(summary = "Добавить в друзья по имени профиля")
    @PostMapping
    public ResponseEntity<Void> addFriend(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @RequestBody @Valid FriendAddRequest request
    ) {
        friendService.add(principal.getId(), request.name());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Убрать друга со своей стороны")
    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> removeFriend(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @PathVariable Long profileId
    ) {
        friendService.remove(principal.getId(), profileId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Онлайн: свои игроки и игроки взаимных друзей, время выхода")
    @GetMapping("/online")
    public List<FriendOnlineInfo> getOnline(@AuthenticationPrincipal ApplicationClientPrincipal principal) {
        return friendService.findOnline(principal.getId());
    }
}
