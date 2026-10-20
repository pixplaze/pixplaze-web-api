package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.db.tables.pojos.ProfileFriend;
import com.pixplaze.api.web.data.dto.ProfileInfo;
import com.pixplaze.api.web.data.dto.UserInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.exception.http.BadRequestException;
import com.pixplaze.api.web.service.ProfileFriendService;
import com.pixplaze.api.web.service.ProfileService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/profiles")
@AllArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileFriendService profileFriendService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{profileId}")
    public UserInfo getProfile(
            @PathVariable(required = false) Long profileId,
            @AuthenticationPrincipal ApplicationClientPrincipal clientPrincipal
    ) {
        if (!clientPrincipal.getAuthority().is(Authority.Role.ADMIN) &&
            !clientPrincipal.getId().equals(profileId)) {
            profileId = clientPrincipal.getId();
        }

        if (clientPrincipal.getAuthority().is(Authority.Role.ADMIN) && profileId == null) {
            throw new IllegalArgumentException("Profile id must be specified!");
        }

        return profileService.getUserInfo(profileId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<ProfileInfo> getProfiles(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return profileService.getProfiles(limit, offset);
    }

    @PreAuthorize("isAuthenticated() AND @profileSecurity.canView(clientPrincipal, #profileId)")
    @GetMapping("/{profileId}/friends")
    public List<Profile> getFriendsProfiles(
        @PathVariable Long profileId,
        @AuthenticationPrincipal ApplicationClientPrincipal clientPrincipal
    ) {
        return profileFriendService.findFriendsProfiles(profileId);
    }

    @PreAuthorize("isAuthenticated() AND @profileSecurity.canView(clientPrincipal, #profileId)")
    @GetMapping("/{profileId}/friends/requests/incoming")
    public List<ProfileFriend> getFriendsProfilesRequestsIncoming(
        @PathVariable Long profileId,
        @AuthenticationPrincipal ApplicationClientPrincipal clientPrincipal
    ) {
        return profileFriendService.findFriendsProfilesRequestsIncoming(profileId);
    }

    @PreAuthorize("isAuthenticated() AND @profileSecurity.canView(clientPrincipal, #profileId)")
    @GetMapping("/{profileId}/friends/requests/outgoing")
    public List<ProfileFriend> getFriendsProfilesRequestsOutgoing(
        @PathVariable Long profileId,
        @AuthenticationPrincipal ApplicationClientPrincipal clientPrincipal
    ) {
        return profileFriendService.findFriendsProfilesRequestsOutgoing(profileId);
    }

    @PreAuthorize("isAuthenticated() AND !@profileSecurity.isTheSameUser(clientPrincipal, #profileId")
    @PutMapping("/me/friends/{friendId}")
    public ProfileFriend addFriend(
        @PathVariable Long friendId,
        @AuthenticationPrincipal ApplicationClientPrincipal clientPrincipal
    ) {
        return profileFriendService.add(clientPrincipal.getId(), friendId);
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/me/friends/{friendId}")
    public void removeFriend(
        @PathVariable Long friendId,
        @AuthenticationPrincipal ApplicationClientPrincipal clientPrincipal
    ) {
        profileFriendService.remove(clientPrincipal.getId(), friendId);
    }

}
