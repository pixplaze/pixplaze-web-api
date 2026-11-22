package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.dto.FriendOnlineInfo;
import com.pixplaze.api.web.data.dto.ProfileFriendship;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.service.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    public List<ProfileFriendship> getFriends(@AuthenticationPrincipal ApplicationClientPrincipal principal) {
        return friendService.findFriends(principal.getId());
    }

    @Operation(summary = "Поиск профилей по началу имени профиля или ника игрока: от 2 символов, до 20 результатов")
    @GetMapping("/search")
    public List<ProfileFriendship> search(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "50") Integer limit,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset
    ) {
        return friendService.search(principal.getId(), query, limit, offset);
    }

    @Operation(summary = "Добавить в друзья по имени профиля")
    @PostMapping("/{profileId}")
    public ResponseEntity<Void> addFriend(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @PathVariable Long profileId
    ) {
        friendService.add(principal.getId(), profileId);
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
