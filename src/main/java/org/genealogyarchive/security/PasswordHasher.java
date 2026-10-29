package org.genealogyarchive.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Mature password hashing (BUILD-SPEC s.43): bcrypt, cost 12, stored with an algorithm prefix
 * ({@code {bcrypt}...}) so the algorithm can be upgraded later without invalidating existing hashes.
 */
public final class PasswordHasher {

    private final PasswordEncoder encoder;

    public PasswordHasher() {
        this(12);
    }

    /** Lower cost is for tests only. */
    public PasswordHasher(int bcryptStrength) {
        this.encoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(bcryptStrength)));
    }

    public String hash(String password) {
        return encoder.encode(password);
    }

    public boolean matches(String password, String hash) {
        return hash != null && encoder.matches(password, hash);
    }

    /** The encoder used by the web sign-in. */
    public PasswordEncoder encoder() {
        return encoder;
    }
}
