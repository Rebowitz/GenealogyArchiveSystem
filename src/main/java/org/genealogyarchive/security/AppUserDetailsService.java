package org.genealogyarchive.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Local accounts for web sign-in (no external identity provider). */
public final class AppUserDetailsService implements UserDetailsService {

    private final UserService users;

    public AppUserDetailsService(UserService users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.credentials(username)
                .map(c -> (UserDetails) new AppUserPrincipal(c.user(), c.passwordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
