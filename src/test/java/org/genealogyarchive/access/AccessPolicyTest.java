package org.genealogyarchive.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SEC-001/002/003 permission matrix as implemented in Phase 1. */
class AccessPolicyTest {

    private static Actor as(Role role) {
        return Actor.user(UUID.randomUUID(), role.name().toLowerCase(), role);
    }

    @ParameterizedTest
    @EnumSource(value = Permission.class, names = "INITIALIZE_ARCHIVE", mode = EnumSource.Mode.EXCLUDE)
    void administratorHoldsEveryPhase1PermissionExceptArchiveInitialization(Permission p) {
        assertThat(AccessPolicy.allows(as(Role.ADMINISTRATOR), p)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void noSignedInRoleMayInitializeTheArchive(Role role) {
        assertThat(AccessPolicy.allows(as(role), Permission.INITIALIZE_ARCHIVE)).isFalse();
        assertThat(AccessPolicy.allows(Actor.commandLine(), Permission.INITIALIZE_ARCHIVE)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"EDITOR", "VIEWER"})
    void editorAndViewerMayOnlyReadAndChangeTheirOwnPasswordInPhase1(Role role) {
        for (Permission p : Permission.values()) {
            boolean expected = p == Permission.VIEW_ARCHIVE || p == Permission.CHANGE_OWN_PASSWORD;
            assertThat(AccessPolicy.allows(as(role), p)).as(role + " " + p).isEqualTo(expected);
        }
    }

    @Test
    void deniedPermissionThrowsWithoutDetail() {
        assertThatThrownBy(() -> AccessPolicy.require(as(Role.VIEWER), Permission.MANAGE_USERS))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You do not have permission for this operation.");
        assertThat(AccessPolicy.allows(null, Permission.VIEW_ARCHIVE)).isFalse();
    }

    @Test
    void actorKindsAreConsistent() {
        assertThatThrownBy(() -> new Actor(Actor.Kind.USER, null, "x", Role.VIEWER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Actor(Actor.Kind.SYSTEM, UUID.randomUUID(), "x", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Actor.commandLine().isSystem()).isTrue();
    }
}
