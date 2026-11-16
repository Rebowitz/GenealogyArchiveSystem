package org.genealogyarchive.data;

import org.genealogyarchive.data.LockSequence.Acquisition;
import org.genealogyarchive.data.LockSequence.Level;
import org.genealogyarchive.data.LockSequence.Mode;
import org.genealogyarchive.data.LockSequence.Tracker;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The global lock order (clarifications s.5.5.3 and s.10.7; review finding PR4-R3): LO-2 (the runtime guard) and
 * LO-3 (no unregistered archival row lock). LO-1 for the accepted Phase 3 paths is {@link LockSequenceIT}.
 */
class LockSequenceTest {

    private static final UUID LOW = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID HIGH = UUID.fromString("00000000-0000-4000-8000-000000000002");
    /** Sorts after LOW and HIGH in PostgreSQL's unsigned order although its signed Java value is negative. */
    private static final UUID TOP = UUID.fromString("f0000000-0000-4000-8000-000000000000");

    /** Archival tables whose explicit row locks must be announced (Phase 3 L1 and the Phase 4 levels). */
    static final Set<String> ARCHIVAL_TABLES = Set.of("capture_session", "file_capture_context", "archival_file",
            "archival_record", "repository", "source", "restriction_operation", "person", "place", "mention",
            "collective_reference", "person_identity_proposal");

    private static Acquisition a(Level level, UUID id, Mode mode) {
        return new Acquisition(level, level.name().toLowerCase(), id, mode);
    }

    @Test
    void lo2_increasingLevelsAndUuidsAreAccepted() {
        Tracker t = new Tracker(true);
        assertThatCode(() -> {
            t.acquire(a(Level.L0_RESTRICTION_OPERATION, LOW, Mode.UPDATE));
            t.acquire(a(Level.L2_PERSON, LOW, Mode.UPDATE));
            t.acquire(a(Level.L3_MENTION, LOW, Mode.UPDATE));
            t.acquire(a(Level.L3_MENTION, HIGH, Mode.UPDATE));
            t.acquire(a(Level.L3_MENTION, TOP, Mode.UPDATE));
            t.acquire(a(Level.L5_IDENTITY_PROPOSAL, LOW, Mode.UPDATE));
        }).doesNotThrowAnyException();
        assertThat(t.acquisitions()).hasSize(6);
    }

    @Test
    void lo2_skippingLevelsIsAllowed() {
        Tracker t = new Tracker(true);
        t.acquire(a(Level.L0_RESTRICTION_OPERATION, LOW, Mode.UPDATE));
        assertThatCode(() -> t.acquire(a(Level.L4_COLLECTIVE, LOW, Mode.UPDATE))).doesNotThrowAnyException();
    }

    @Test
    void lo2_aReversedLevelIsRejected() {
        Tracker t = new Tracker(true);
        t.acquire(a(Level.L3_MENTION, LOW, Mode.UPDATE));
        assertThatThrownBy(() -> t.acquire(a(Level.L2_PERSON, HIGH, Mode.SHARE)))
                .isInstanceOf(LockSequence.LockOrderViolation.class).hasMessageContaining("reversed");
        Tracker u = new Tracker(true);
        u.acquire(a(Level.L2_PERSON, LOW, Mode.SHARE));
        assertThatThrownBy(() -> u.acquire(a(Level.L1_PHASE3, HIGH, Mode.SHARE)))
                .as("no L1 lock after a higher level").isInstanceOf(LockSequence.LockOrderViolation.class);
        Tracker v = new Tracker(true);
        v.acquire(a(Level.L2_PLACE, LOW, Mode.SHARE));
        assertThatThrownBy(() -> v.acquire(a(Level.L2_PERSON, HIGH, Mode.SHARE)))
                .as("Persons before Places").isInstanceOf(LockSequence.LockOrderViolation.class);
    }

    @Test
    void lo2_rowsOfOneLevelOutOfUuidOrderAreRejected() {
        Tracker t = new Tracker(true);
        t.acquire(a(Level.L3_MENTION, TOP, Mode.UPDATE));
        assertThatThrownBy(() -> t.acquire(a(Level.L3_MENTION, HIGH, Mode.UPDATE)))
                .isInstanceOf(LockSequence.LockOrderViolation.class).hasMessageContaining("UUID order");
    }

    @Test
    void lo2_aShareToUpdateUpgradeIsRejected() {
        Tracker t = new Tracker(true);
        t.acquire(a(Level.L2_PERSON, LOW, Mode.SHARE));
        assertThatThrownBy(() -> t.acquire(a(Level.L2_PERSON, LOW, Mode.UPDATE)))
                .isInstanceOf(LockSequence.LockOrderViolation.class).hasMessageContaining("upgrade");
        Tracker u = new Tracker(true);
        u.acquire(a(Level.L2_PERSON, LOW, Mode.UPDATE));
        assertThatCode(() -> u.acquire(a(Level.L2_PERSON, LOW, Mode.SHARE))).as("re-locking weaker is harmless")
                .doesNotThrowAnyException();
    }

    // ---- WP1-R3: upgrades and re-locks at every level --------------------------------------------

    private static Acquisition l1(UUID id, Mode mode) {
        return new Acquisition(Level.L1_PHASE3, "archival_file", id, mode);
    }

    @Test
    void wp1r3_aLevel1ShareToUpdateUpgradeIsRejected() {
        Tracker t = new Tracker(true);
        t.acquire(l1(LOW, Mode.SHARE));
        assertThatThrownBy(() -> t.acquire(l1(LOW, Mode.UPDATE)))
                .isInstanceOf(LockSequence.LockOrderViolation.class).hasMessageContaining("upgrade");
    }

    @Test
    void wp1r3_aLevel1UpgradeAfterAHigherLevelIsRejected() {
        Tracker t = new Tracker(true);
        t.acquire(l1(LOW, Mode.SHARE));
        t.acquire(a(Level.L2_PERSON, HIGH, Mode.UPDATE));
        assertThatThrownBy(() -> t.acquire(l1(LOW, Mode.UPDATE)))
                .isInstanceOf(LockSequence.LockOrderViolation.class).hasMessageContaining("upgrade");
    }

    @Test
    void wp1r3_aPreviouslySeenRowCannotBypassTheLevelOrder() {
        Tracker t = new Tracker(true);
        t.acquire(l1(LOW, Mode.SHARE));
        t.acquire(a(Level.L2_PERSON, HIGH, Mode.UPDATE));
        assertThatThrownBy(() -> t.acquire(l1(LOW, Mode.SHARE)))
                .as("even a same-strength re-lock below a higher level")
                .isInstanceOf(LockSequence.LockOrderViolation.class).hasMessageContaining("Re-lock");
    }

    @Test
    void wp1r3_aNewLevel1ResourceAfterLevel2IsRejected() {
        Tracker t = new Tracker(true);
        t.acquire(l1(LOW, Mode.SHARE));
        t.acquire(a(Level.L2_PERSON, HIGH, Mode.SHARE));
        assertThatThrownBy(() -> t.acquire(l1(HIGH, Mode.SHARE)))
                .isInstanceOf(LockSequence.LockOrderViolation.class).hasMessageContaining("reversed");
    }

    @Test
    void wp1r3_harmlessReLocksAreAllowedAndRecorded() {
        Tracker t = new Tracker(true);
        assertThatCode(() -> {
            t.acquire(l1(LOW, Mode.UPDATE));
            t.acquire(l1(LOW, Mode.SHARE));            // weaker, same level
            t.acquire(l1(LOW, Mode.UPDATE));           // same strength as held
            t.acquire(a(Level.L3_MENTION, HIGH, Mode.UPDATE));
            t.acquire(a(Level.L3_MENTION, TOP, Mode.UPDATE));
            t.acquire(a(Level.L3_MENTION, HIGH, Mode.SHARE));  // held row: exempt from the UUID check
        }).doesNotThrowAnyException();
        assertThat(t.acquisitions()).hasSize(6);
    }

    @Test
    void wp1r3_aRowIsAlwaysAnnouncedAtOneLevel() {
        Tracker t = new Tracker(true);
        t.acquire(new Acquisition(Level.L2_PERSON, "person", LOW, Mode.SHARE));
        assertThatThrownBy(() -> t.acquire(new Acquisition(Level.L3_MENTION, "person", LOW, Mode.SHARE)))
                .isInstanceOf(LockSequence.LockOrderViolation.class).hasMessageContaining("first locked at");
    }

    @Test
    void lo2_level1KeepsTheAcceptedPhase3OrderUnchecked() {
        // Phase 3 grouping locks worksheet rows and Files in plan order and may lock a classification row after the
        // Files (ADR-0011; GAS-P3-004). L1's internal order is Phase 3's own and is not re-checked.
        Tracker t = new Tracker(true);
        assertThatCode(() -> {
            t.acquire(new Acquisition(Level.L1_PHASE3, "capture_session", HIGH, Mode.UPDATE));
            t.acquire(new Acquisition(Level.L1_PHASE3, "file_capture_context", TOP, Mode.UPDATE));
            t.acquire(new Acquisition(Level.L1_PHASE3, "archival_file", TOP, Mode.SHARE));
            t.acquire(new Acquisition(Level.L1_PHASE3, "archival_record", LOW, Mode.UPDATE));
            t.acquire(new Acquisition(Level.L1_PHASE3, "file_capture_context", LOW, Mode.UPDATE));
            t.acquire(new Acquisition(Level.L1_PHASE3, "archival_file", LOW, Mode.SHARE));
        }).doesNotThrowAnyException();
    }

    @Test
    void lo2_withoutTheGuardNothingIsChecked() {
        Tracker t = new Tracker(false);
        t.acquire(a(Level.L5_IDENTITY_PROPOSAL, LOW, Mode.UPDATE));
        assertThatCode(() -> t.acquire(a(Level.L0_RESTRICTION_OPERATION, LOW, Mode.UPDATE))).doesNotThrowAnyException();
    }

    @Test
    void lo2_theGuardIsOnForTestRuns() {
        assertThat(LockSequence.guardEnabled()).as("-Dgas.lockOrderGuard=true is set for every test run").isTrue();
    }

    @Test
    void uuidOrderMatchesPostgresql() {
        assertThat(LockSequence.compareUnsigned(LOW, HIGH)).isNegative();
        assertThat(LockSequence.compareUnsigned(HIGH, TOP)).isNegative();
        assertThat(TOP.compareTo(HIGH)).as("java.util.UUID is signed").isNegative();
    }

    /**
     * LO-3: every explicit row lock on an archival table is announced through {@link LockSequence} in the same
     * method, so the guard and the observers see it.
     */
    @Test
    void lo3_everyArchivalRowLockIsRegistered() throws IOException {
        Path data = Path.of("src/main/java/org/genealogyarchive/data");
        assertThat(data).isDirectory();
        Pattern lock = Pattern.compile("FOR (?:UPDATE|SHARE|NO KEY UPDATE|KEY SHARE)\\b");
        Pattern table = Pattern.compile("gas\\.(\\w+)");
        List<String> unregistered = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(data)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.startsWith("*") || line.startsWith("/") || !lock.matcher(line).find()) {
                        continue;
                    }
                    // The locked table: the nearest "gas.<table>" on this line or above it in the same method.
                    String locked = null;
                    for (int j = i; j >= 0 && locked == null; j--) {
                        Matcher m = table.matcher(lines.get(j));
                        String last = null;
                        while (m.find()) {
                            last = m.group(1);
                        }
                        locked = last;
                        if (j < i && isDeclaration(lines.get(j))) {
                            break;
                        }
                    }
                    if (locked != null && !ARCHIVAL_TABLES.contains(locked)) {
                        continue; // a non-archival table (users, jobs, preservation internals)
                    }
                    checked++;
                    if (!announcedInMethod(lines, i, locked)) {
                        unregistered.add(f.getFileName() + ":" + (i + 1) + " " + (locked == null ? "?" : locked));
                    }
                }
            }
        }
        assertThat(checked).as("Phase 3 lock statements found").isGreaterThanOrEqualTo(7);
        assertThat(unregistered).isEmpty();
    }

    private static boolean isDeclaration(String line) {
        return line.matches("\\s*(public|private|protected|static)[^=;]*\\(.*");
    }

    /**
     * True if the lock is announced between the enclosing method's declaration and line {@code at}: for a known
     * table, an announcement naming it; for a statement whose table is defined elsewhere, any announcement.
     */
    private static boolean announcedInMethod(List<String> lines, int at, String table) {
        for (int i = at; i >= 0; i--) {
            String l = lines.get(i);
            if (l.contains("LockSequence.acquire(") && (table == null || l.contains("\"" + table + "\""))) {
                return true;
            }
            if (i < at && isDeclaration(l)) {
                return false;
            }
        }
        return false;
    }
}
