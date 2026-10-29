package org.genealogyarchive.security;

import org.genealogyarchive.service.RuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void enforcesMinimumLength() {
        assertThatThrownBy(() -> PasswordPolicy.check("alice", "short-pass1")).isInstanceOf(RuleViolationException.class);
        assertThatCode(() -> PasswordPolicy.check("alice", "twelve-chars")).doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordsBcryptWouldSilentlyTruncate() {
        assertThatCode(() -> PasswordPolicy.check("alice", "a".repeat(72))).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.check("alice", "a".repeat(73))).isInstanceOf(RuleViolationException.class);
        // 25 three-byte characters = 75 bytes although only 25 characters.
        assertThatThrownBy(() -> PasswordPolicy.check("alice", "€".repeat(25))).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void rejectsUsernameAsPassword() {
        assertThatThrownBy(() -> PasswordPolicy.check("genealogist01", "GENEALOGIST01")).isInstanceOf(RuleViolationException.class);
    }

    @Test
    void hashesAreSaltedPrefixedAndVerifiable() {
        PasswordHasher hasher = new PasswordHasher(4);
        String a = hasher.hash("correct-horse-battery");
        String b = hasher.hash("correct-horse-battery");
        assertThat(a).startsWith("{bcrypt}").isNotEqualTo(b).doesNotContain("correct-horse-battery");
        assertThat(hasher.matches("correct-horse-battery", a)).isTrue();
        assertThat(hasher.matches("wrong-password-x", a)).isFalse();
        assertThat(hasher.matches("anything-at-all", null)).isFalse();
    }
}
