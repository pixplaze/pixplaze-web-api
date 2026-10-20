package com.pixplaze.api.web.util;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

@Component("profileSecurity")
public class ProfileSecurityEvaluator {

  public static boolean canView(ApplicationClientPrincipal principal, Long profileId) {
    if(principal == null) return false;

    return principal.getAuthority().is(Authority.Role.ADMIN) ||
        isTheSameUser(principal, profileId);
  }

  public static boolean isTheSameUser(@NotNull ApplicationClientPrincipal principal, Long profileId) {
    return principal.getId().equals(profileId);
  }

}
