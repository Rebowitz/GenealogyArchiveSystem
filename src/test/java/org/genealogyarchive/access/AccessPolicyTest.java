package org.genealogyarchive.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SEC-001/002/003 permission matrix as implemented in Phase 1. */
class AccessPolicyTest {

    private static final java.util.List<Permission> PHASE_1_PERMISSIONS = java.util.List.of(
            Permission.VIEW_ARCHIVE, Permission.CHANGE_OWN_PASSWORD, Permission.CONFIGURE_ARCHIVE,
            Permission.MANAGE_USERS, Permission.MANAGE_STORAGE_VOLUMES, Permission.VIEW_OPERATIONS,
            Permission.RETRY_JOBS, Permission.INITIALIZE_ARCHIVE);

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
        // Phase 2 scoping: the loop covers the Phase 1 permissions; Phase 2 grants are tested separately.
        for (Permission p : PHASE_1_PERMISSIONS) {
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

    @Test
    void phase1PermissionListCoversEveryPhase1Permission() {
        java.util.Set<Permission> phase2 = java.util.EnumSet.of(Permission.INGEST_FILES, Permission.EDIT_FILES,
                Permission.DESTROY_FILES, Permission.MANAGE_PRESERVATION, Permission.VIEW_RESTRICTED_FILES);
        java.util.Set<Permission> all = java.util.EnumSet.allOf(Permission.class);
        all.removeAll(phase2);
        all.removeAll(PHASE_3_PERMISSIONS);
        assertThat(PHASE_1_PERMISSIONS).as("no permission escapes the Phase 1, Phase 2 and Phase 3 checks")
                .containsExactlyInAnyOrderElementsOf(all);
    }

    @Test
    void phase2EditorsIngestAndEditFilesButNeverDestroyOrSeeRestrictedContent() {
        Actor editor = as(Role.EDITOR);
        assertThat(AccessPolicy.allows(editor, Permission.INGEST_FILES)).isTrue();
        assertThat(AccessPolicy.allows(editor, Permission.EDIT_FILES)).isTrue();
        assertThat(AccessPolicy.allows(editor, Permission.DESTROY_FILES)).as("SEC-002").isFalse();
        assertThat(AccessPolicy.allows(editor, Permission.MANAGE_PRESERVATION)).isFalse();
        assertThat(AccessPolicy.allows(editor, Permission.VIEW_RESTRICTED_FILES)).as("B2").isFalse();
    }

    @Test
    void phase2ViewersHaveNoFilePermissions() {
        Actor viewer = as(Role.VIEWER);
        for (Permission p : java.util.List.of(Permission.INGEST_FILES, Permission.EDIT_FILES, Permission.DESTROY_FILES,
                Permission.MANAGE_PRESERVATION, Permission.VIEW_RESTRICTED_FILES)) {
            assertThat(AccessPolicy.allows(viewer, p)).as("SEC-003 " + p).isFalse();
        }
    }

    private static final java.util.List<Permission> PHASE_3_PERMISSIONS = java.util.List.of(
            Permission.EDIT_RECORDS, Permission.VIEW_RESTRICTED_RECORDS, Permission.MANAGE_VOCABULARIES);

    /** SEC-001/002/003 for Phase 3: Editors enter records; restricted Records and vocabularies are Administrator-only. */
    @Test
    void phase3EditorsEnterRecordsButNeitherSeeRestrictedRecordsNorManageVocabularies() {
        assertThat(AccessPolicy.allows(as(Role.ADMINISTRATOR), Permission.EDIT_RECORDS)).isTrue();
        assertThat(AccessPolicy.allows(as(Role.ADMINISTRATOR), Permission.VIEW_RESTRICTED_RECORDS)).isTrue();
        assertThat(AccessPolicy.allows(as(Role.ADMINISTRATOR), Permission.MANAGE_VOCABULARIES)).isTrue();
        Actor editor = as(Role.EDITOR);
        assertThat(AccessPolicy.allows(editor, Permission.EDIT_RECORDS)).as("SEC-002").isTrue();
        assertThat(AccessPolicy.allows(editor, Permission.VIEW_RESTRICTED_RECORDS)).as("D3-04").isFalse();
        assertThat(AccessPolicy.allows(editor, Permission.MANAGE_VOCABULARIES)).as("D3-08").isFalse();
        for (Permission p : PHASE_3_PERMISSIONS) {
            assertThat(AccessPolicy.allows(as(Role.VIEWER), p)).as("SEC-003 " + p).isFalse();
        }
    }
}
