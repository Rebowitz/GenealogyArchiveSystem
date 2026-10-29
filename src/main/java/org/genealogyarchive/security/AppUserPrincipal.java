package org.genealogyarchive.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in user as held in the web session. Holds the user's global ID; role and enabled state
 * are re-read from the database on every request, so changes apply immediately.
 */
public final class AppUserPrincipal implements UserDetails {

    private final UUID userId;
    private final String username;
    private final transient String passwordHash;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    AppUserPrincipal(AppUser user, String passwordHash) {
        this.userId = user.userId();
        this.username = user.username();
        this.passwordHash = passwordHash;
        this.enabled = user.enabled();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
    }

    public UUID userId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[" + username + "]";
    }
}
