package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.dto.ProfileInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
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

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{profileId}")
    public ProfileInfo getProfile(
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

        return profileService.getProfileInfo(profileId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<Profile> getProfiles(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        return profileService.getProfiles(limit, offset);
    }

}
