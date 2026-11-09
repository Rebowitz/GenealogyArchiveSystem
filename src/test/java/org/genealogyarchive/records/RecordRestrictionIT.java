package org.genealogyarchive.records;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.testsupport.RecordsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Restriction traversal (D3-04, P2-H; plan section 10.3; ADR-0011 s.8). For a non-Administrator a restricted Record
 * or File and a nonexistent one are indistinguishable on every path, and a NORMAL Record never discloses its
 * restricted Files: no link, page, count, position gap, grouping data or history.
 */
class RecordRestrictionIT {

    @TempDir
    Path temp;
    RecordsFixture rx;

    @BeforeEach
    void setUp() throws IOException {
        rx = RecordsFixture.create(temp);
    }

    @AfterEach
    void tearDown() {
        rx.close();
    }

    private RecordService records() {
        return rx.records().records();
    }

    private List<Actor> nonAdministrators() {
        return List.of(rx.editor, rx.viewer);
    }

    /** The exception a reader gets, as type and message: a hidden object must answer exactly like a missing one. */
    private static String answer(Runnable r) {
        Throwable t = catchThrowable(r::run);
        return t == null ? "ok" : t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    private ArchivalRecord restrictedRecord() {
        ArchivalRecord r = records().create(rx.admin, new RecordService.RecordInput("DEED", "sealed adoption deed (invented)",
                null, null, null, null, null, null, Restriction.RESTRICTED));
        assertThat(r.restricted()).isTrue();
        return r;
    }

    private List<String> historyText(Actor actor, String objectType, UUID id) {
        return rx.fx.services().history().forObject(actor, objectType, id).stream().flatMap(RecordRestrictionIT::text).toList();
    }

    private static Stream<String> text(ChangeRecord c) {
        Stream<String> fields = c.fieldChanges().stream()
                .flatMap(fc -> Stream.of(fc.field(), fc.before(), fc.after())).filter(v -> v != null);
        return Stream.concat(fields, Stream.ofNullable(c.reason()));
    }

    // ---- restricted Records ----------------------------------------------------------------------

    @Test
    void aRestrictedRecordIsIndistinguishableFromANonexistentOne() {
        ArchivalRecord r = restrictedRecord();
        Citation c = rx.records().citations().create(rx.admin, null, r.recordId(), "p. 1", null, null);
        ArchivalFile f = rx.file();
        ArchivalRecord rr = records().get(rx.admin, r.recordId());
        records().linkFile(rx.admin, rr.recordId(), rr.version(), f.fileId(), "1", null);
        UUID missing = UUID.randomUUID();
        for (Actor a : nonAdministrators()) {
            assertThat(answer(() -> records().get(a, r.recordId()))).isEqualTo(answer(() -> records().get(a, missing)));
            assertThat(answer(() -> records().getByFriendlyId(a, r.friendlyId())))
                    .isEqualTo(answer(() -> records().getByFriendlyId(a, "R99999")));
            assertThat(answer(() -> records().view(a, r.recordId()))).isEqualTo(answer(() -> records().view(a, missing)));
            assertThat(answer(() -> records().history(a, r.recordId(), rx.fx.services().history())))
                    .isEqualTo(answer(() -> records().history(a, missing, rx.fx.services().history())));
            assertThat(records().list(a, true, null, null, null, 500)).extracting(ArchivalRecord::recordId)
                    .doesNotContain(r.recordId());
            assertThat(records().usesOfFile(a, f.fileId())).isEmpty();
            assertThat(answer(() -> rx.records().citations().get(a, c.citationId())))
                    .isEqualTo(answer(() -> rx.records().citations().get(a, missing)));
            assertThat(rx.fx.services().history().recent(a, 500)).extracting(ChangeRecord::objectId)
                    .doesNotContain(r.recordId(), c.citationId());
        }
        // Editors cannot change it either: it does not exist for them.
        assertThat(answer(() -> records().correct(rx.editor, r.recordId(), rr.version(), new RecordService.RecordInput(
                null, "x", null, null, null, null, null, null, null), null)))
                .isEqualTo(answer(() -> records().correct(rx.editor, missing, 1, new RecordService.RecordInput(
                        null, "x", null, null, null, null, null, null, null), null)));
        assertThat(answer(() -> rx.records().citations().create(rx.editor, null, r.recordId(), "x", null, null)))
                .isEqualTo(answer(() -> rx.records().citations().create(rx.editor, null, missing, "x", null, null)));
        // Administrators see everything.
        assertThat(records().view(rx.admin, r.recordId()).files()).hasSize(1);
        assertThat(records().usesOfFile(rx.admin, f.fileId())).hasSize(1);
    }

    /** A reference to a restricted Record in another object's history is withheld for non-Administrators. */
    @Test
    void historyNeverNamesARestrictedRecord() {
        ArchivalRecord r = restrictedRecord();
        ArchivalRecord normal = records().create(rx.admin, new RecordService.RecordInput(null, null, null, null, null, null,
                null, null, null));
        records().correct(rx.admin, normal.recordId(), normal.version(), new RecordService.RecordInput(null,
                "see " + r.friendlyId(), null, null, null, null, null, null, null), "cross-checked with " + r.recordId());
        for (Actor a : nonAdministrators()) {
            assertThat(historyText(a, "archival_record", normal.recordId()))
                    .noneMatch(t -> t.contains(r.friendlyId()) || t.contains(r.recordId().toString()));
        }
        assertThat(historyText(rx.admin, "archival_record", normal.recordId())).anyMatch(t -> t.contains(r.friendlyId()));
    }

    // ---- NORMAL Records with restricted Files (mixed visibility) ---------------------------------

    @Test
    void aNormalRecordNeverDisclosesItsRestrictedFiles() {
        ArchivalFile a = rx.file();
        ArchivalFile h = rx.restrictedFile();
        ArchivalFile b = rx.file();
        ArchivalFile d = rx.fx.file(rx.file().fileId());
        ArchivalRecord r = records().create(rx.admin, new RecordService.RecordInput(null, null, null, null, null, null,
                null, null, null));
        for (ArchivalFile f : List.of(a, h, b, d)) {
            ArchivalRecord c = records().get(rx.admin, r.recordId());
            records().linkFile(rx.admin, c.recordId(), c.version(), f.fileId(), f == h ? "SECRET-PAGE" : "1", f == h ? "secret note" : null);
        }
        // d is deleted (still visible, marked), h is restricted (hidden).
        rx.fx.preservation().files().delete(rx.editor, d.fileId(), d.version(), "blurred");

        for (Actor actor : nonAdministrators()) {
            RecordService.RecordView view = records().view(actor, r.recordId());
            assertThat(view.files()).extracting(l -> l.file().fileId()).containsExactly(a.fileId(), b.fileId(), d.fileId());
            assertThat(view.files()).extracting(l -> l.link().pageDesignation()).doesNotContain("SECRET-PAGE");
            assertThat(answer(() -> records().usesOfFile(actor, h.fileId())))
                    .isEqualTo(answer(() -> records().usesOfFile(actor, UUID.randomUUID())));
            List<String> hist = historyText(actor, "archival_record", r.recordId());
            assertThat(hist).noneMatch(t -> t.contains(h.friendlyId()) || t.contains("SECRET-PAGE") || t.contains(h.fileId().toString()));
            assertThat(hist).as("no stored sequence numbers in history").noneMatch(t -> t.equals("sequence"));
        }
        // Editors operate on such a link exactly as on a nonexistent one.
        ArchivalRecord c = records().get(rx.admin, r.recordId());
        assertThat(answer(() -> records().unlinkFile(rx.editor, c.recordId(), c.version(), h.fileId(), null)))
                .isEqualTo(answer(() -> records().unlinkFile(rx.editor, c.recordId(), c.version(), UUID.randomUUID(), null)));
        assertThat(answer(() -> records().correctLink(rx.editor, c.recordId(), c.version(), h.fileId(), "x", null)))
                .isEqualTo(answer(() -> records().correctLink(rx.editor, c.recordId(), c.version(), UUID.randomUUID(), "x", null)));
        // Moving b earlier swaps it with a (its visible neighbour); the hidden link keeps its place.
        records().moveLink(rx.editor, c.recordId(), c.version(), b.fileId(), true);
        assertThat(records().view(rx.admin, r.recordId()).files()).extracting(l -> l.file().fileId())
                .containsExactly(b.fileId(), h.fileId(), a.fileId(), d.fileId());
        assertThat(records().view(rx.editor, r.recordId()).files()).extracting(l -> l.file().fileId())
                .containsExactly(b.fileId(), a.fileId(), d.fileId());
        assertThat(records().view(rx.admin, r.recordId()).files()).hasSize(4);
    }

    // ---- capture sessions ------------------------------------------------------------------------

    @Test
    void sessionsNeverDiscloseRestrictedRows() {
        ArchivalFile a = rx.file();
        ArchivalFile h = rx.restrictedFile();
        ArchivalFile b = rx.file();
        CaptureSession s = rx.session("Mixed session");
        rx.add(s, List.of(a, h, b));
        rx.mark(s, h, true, "hidden group", "SECRET-PAGE", null);
        for (Actor actor : nonAdministrators()) {
            assertThat(rx.records().sessions().worksheet(actor, s.captureSessionId(), 0, 100).rows())
                    .extracting(w -> w.file().fileId()).containsExactly(a.fileId(), b.fileId());
            assertThat(answer(() -> rx.records().sessions().context(actor, h.fileId())))
                    .isEqualTo(answer(() -> rx.records().sessions().context(actor, UUID.randomUUID())));
            List<String> hist = historyText(actor, "capture_session", s.captureSessionId());
            assertThat(hist).noneMatch(t -> t.contains(h.friendlyId()) || t.contains("SECRET-PAGE") || t.contains("hidden group"));
            assertThat(hist).noneMatch(t -> t.equals("sequence"));
        }
        // Saving marks or overrides for a hidden File answers exactly as for a nonexistent one.
        CaptureRow hr = rx.row(h.fileId());
        UUID missing = UUID.randomUUID();
        assertThat(answer(() -> rx.records().sessions().saveMarks(rx.editor, s.captureSessionId(), List.of(
                new CaptureSessionService.MarkEdit(h.fileId(), hr.version(), false, null, null, null)))))
                .isEqualTo(answer(() -> rx.records().sessions().saveMarks(rx.editor, s.captureSessionId(), List.of(
                        new CaptureSessionService.MarkEdit(missing, 1, false, null, null, null)))));
        assertThat(answer(() -> rx.records().sessions().removeFile(rx.editor, h.fileId(), hr.version(), null)))
                .isEqualTo(answer(() -> rx.records().sessions().removeFile(rx.editor, missing, 1, null)));
        // Adding a restricted File by ID counts it exactly like an ID that does not exist.
        CaptureSession other = rx.session("Other");
        CaptureSessionService.AddResult added = rx.records().sessions().addByFriendlyIds(rx.editor,
                other.captureSessionId(), rx.restrictedFile().friendlyId() + ", F99999");
        assertThat(added.notAvailable()).isEqualTo(2);
        // Applying through a hidden File answers like a missing one.
        assertThat(answer(() -> rx.records().grouping().apply(rx.editor, s.captureSessionId(), h.fileId())))
                .isEqualTo(answer(() -> rx.records().grouping().apply(rx.editor, s.captureSessionId(), missing)));
    }

    /** A Citation set over both kinds of Record shows non-Administrators only the visible ones. */
    @Test
    void citationListsOmitCitationsOfRestrictedRecords() {
        Source s = rx.records().catalog().createSource(rx.admin, new CatalogService.SourceInput("Book", null, null, null,
                null, null, null, null));
        ArchivalRecord hidden = restrictedRecord();
        ArchivalRecord open = records().create(rx.admin, new RecordService.RecordInput(null, null, s.sourceId(), null, null,
                null, null, null, null));
        Citation c1 = rx.records().citations().create(rx.admin, s.sourceId(), hidden.recordId(), "1", null, null);
        Citation c2 = rx.records().citations().create(rx.admin, s.sourceId(), open.recordId(), "2", null, null);
        Citation c3 = rx.records().citations().create(rx.admin, s.sourceId(), null, "3", null, null);
        for (Actor a : nonAdministrators()) {
            assertThat(rx.records().citations().ofSource(a, s.sourceId())).extracting(Citation::citationId)
                    .containsExactly(c2.citationId(), c3.citationId());
        }
        assertThat(rx.records().citations().ofSource(rx.admin, s.sourceId())).extracting(Citation::citationId)
                .containsExactly(c1.citationId(), c2.citationId(), c3.citationId());
    }

    /** Restriction never propagates (D3-04): restricting a File leaves the Record NORMAL, and vice versa. */
    @Test
    void restrictionDoesNotPropagate() {
        ArchivalFile f = rx.file();
        ArchivalRecord r = records().create(rx.admin, new RecordService.RecordInput(null, null, null, null, null, null,
                null, null, null));
        records().linkFile(rx.admin, r.recordId(), r.version(), f.fileId(), null, null);
        rx.fx.preservation().files().correct(rx.admin, f.fileId(), f.version(), f.managedFilename(), 0,
                Restriction.RESTRICTED, null);
        assertThat(records().get(rx.admin, r.recordId()).restricted()).isFalse();
        ArchivalRecord c = records().get(rx.admin, r.recordId());
        records().correct(rx.admin, c.recordId(), c.version(), new RecordService.RecordInput(null, null, null, null, null,
                null, null, null, Restriction.RESTRICTED), null);
        ArchivalFile g = rx.file();
        ArchivalRecord c2 = records().get(rx.admin, r.recordId());
        records().linkFile(rx.admin, c2.recordId(), c2.version(), g.fileId(), null, null);
        assertThat(rx.fx.file(g.fileId()).restricted()).isFalse();
        assertThat(catchThrowable(() -> records().get(rx.editor, r.recordId()))).isInstanceOf(NotFoundException.class);
    }

    @SuppressWarnings("unused")
    private static boolean mentions(FieldChange fc, String s) {
        return (fc.before() != null && fc.before().contains(s)) || (fc.after() != null && fc.after().contains(s));
    }
}
