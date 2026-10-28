package org.genealogyarchive.web;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.security.AppUser;
import org.genealogyarchive.security.AppUserPrincipal;
import org.genealogyarchive.security.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Turns the signed-in session into an {@link Actor}. The account is re-read on every request, so a
 * disabled account or changed role takes effect immediately rather than at the next sign-in.
 */
@Component
public class WebActors {

    private final UserService users;

    public WebActors(UserService users) {
        this.users = users;
    }

    public Optional<AppUser> currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return Optional.empty();
        }
        return users.activeUser(principal.userId());
    }

    /** @throws SessionEndedException if the account no longer exists or is disabled */
    public Actor require(Authentication authentication) {
        return currentUser(authentication).map(AppUser::asActor).orElseThrow(SessionEndedException::new);
    }

    /** The signed-in account is no longer active; the session is ended. */
    public static final class SessionEndedException extends RuntimeException {
        public SessionEndedException() {
            super("Your session has ended.");
        }
    }
}
