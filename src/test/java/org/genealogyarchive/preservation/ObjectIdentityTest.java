package org.genealogyarchive.preservation;

import org.genealogyarchive.data.UnresolvedObjectRepository.Identity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** V0020: how object-identity evidence is compared (exact equality; a missing file key is not a wildcard). */
class ObjectIdentityTest {

    private static final String SHA = "ab".repeat(32);
    private static final Instant CREATED = Instant.parse("2026-03-01T10:15:30.123456789Z");
    private static final Instant MODIFIED = Instant.parse("2026-03-02T11:00:00.000001999Z");

    @Test
    void identicalEvidenceWithoutAFileKeyMatchesAsOnWindows() {
        Identity recorded = new Identity(3, SHA, CREATED, MODIFIED, null);
        Identity now = new Identity(3, SHA, CREATED, MODIFIED, null);
        assertThat(recorded).isEqualTo(now);
        assertThat(ObjectIdentity.difference(recorded, now)).isNull();
    }

    @Test
    void timesAreComparedToTheMicrosecondPostgresqlKeeps() {
        Identity recorded = new Identity(3, SHA, CREATED, MODIFIED, null);
        Identity now = new Identity(3, SHA, CREATED.plusNanos(100), MODIFIED.minusNanos(999), null);
        assertThat(ObjectIdentity.difference(recorded, now)).isNull();
        Identity later = new Identity(3, SHA, CREATED, MODIFIED.plusNanos(2000), null);
        assertThat(ObjectIdentity.difference(recorded, later)).contains("filesystem identity differs");
    }

    @Test
    void aFileKeyOnOneSideOnlyIsADifferenceNotAWildcard() {
        Identity withKey = new Identity(3, SHA, CREATED, MODIFIED, "(dev=803,ino=12)");
        Identity without = new Identity(3, SHA, CREATED, MODIFIED, null);
        assertThat(ObjectIdentity.difference(withKey, without)).contains("filesystem identity differs");
        assertThat(ObjectIdentity.difference(without, withKey)).contains("filesystem identity differs");
        assertThat(ObjectIdentity.difference(withKey, new Identity(3, SHA, CREATED, MODIFIED, "(dev=803,ino=13)")))
                .contains("filesystem identity differs");
    }

    @Test
    void differentBytesAndNonFilesAreNamed() {
        Identity recorded = new Identity(3, SHA, CREATED, MODIFIED, null);
        assertThat(ObjectIdentity.difference(recorded, new Identity(4, SHA, CREATED, MODIFIED, null))).contains("bytes differ");
        assertThat(ObjectIdentity.difference(recorded, new Identity(3, "cd".repeat(32), CREATED, MODIFIED, null)))
                .contains("bytes differ");
        assertThat(ObjectIdentity.difference(recorded, null)).contains("not a plain file");
    }
}
