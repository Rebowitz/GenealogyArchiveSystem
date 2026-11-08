package org.genealogyarchive.records;

import org.genealogyarchive.data.CaptureSessionRepository.FieldOverride;
import org.genealogyarchive.data.CaptureSessionRepository.Overrides;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.RecordsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Capture Session inheritance (BI-001, BI-002; plan section 10.2; ADR-0011 s.3): inherit, own value or explicitly
 * not known, per field; resolved when read; session corrections never rewrite File history; overrides are isolated.
 */
class CaptureContextIT {

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

    private CaptureSessionService sessions() {
        return rx.records().sessions();
    }

    private CaptureSessionService.EffectiveContext ctx(ArchivalFile f) {
        return sessions().context(rx.viewer, f.fileId()).orElseThrow();
    }

    private List<ChangeRecord> fileHistory(ArchivalFile f) {
        return rx.fx.services().history().forObject(rx.admin, "archival_file", f.fileId());
    }

    private static Overrides inheritAll() {
        return new Overrides(FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.inherit(),
                FieldOverride.inherit(), FieldOverride.inherit());
    }

    /** BI-001: context entered once is inherited by every File; one File's override changes nothing else. */
    @Test
    void bi001_inheritanceAndOverrideIsolation() {
        Repository repo = rx.records().catalog().createRepository(rx.editor, "County Clerk (invented)", null, null);
        Source book = rx.records().catalog().createSource(rx.editor, new CatalogService.SourceInput("Deed Book 12",
                repo.repositoryId(), null, null, null, null, null, null));
        Source other = rx.records().catalog().createSource(rx.editor, new CatalogService.SourceInput("Deed Book 13",
                repo.repositoryId(), null, null, null, null, null, null));
        CaptureSession s = sessions().create(rx.editor, new CaptureSessionService.SessionInput("Visit", repo.repositoryId(),
                book.sourceId(), LocalDate.of(2026, 10, 1), "R. Researcher", "Phone camera, copy stand", null));
        List<ArchivalFile> files = rx.files(20);
        sessions().addFiles(rx.editor, s.captureSessionId(), files.stream().map(ArchivalFile::fileId).toList());

        for (ArchivalFile f : files) {
            CaptureSessionService.EffectiveContext c = ctx(f);
            assertThat(c.source().orElseThrow().sourceId()).isEqualTo(book.sourceId());
            assertThat(c.researcher()).isEqualTo("R. Researcher");
            assertThat(c.captureDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(c.overridden()).isEmpty();
            assertThat(c.blanked()).isEmpty();
        }
        ArchivalFile odd = files.get(7);
        ArchivalFile unknown = files.get(8);
        sessions().correctOverrides(rx.editor, odd.fileId(), rx.row(odd.fileId()).version(), new Overrides(
                FieldOverride.inherit(), FieldOverride.of(other.sourceId()), FieldOverride.inherit(),
                FieldOverride.inherit(), FieldOverride.of("Flatbed scanner")), "page from the next book");
        sessions().correctOverrides(rx.editor, unknown.fileId(), rx.row(unknown.fileId()).version(), new Overrides(
                FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.blank(), FieldOverride.inherit(),
                FieldOverride.inherit()), "date not known for this one");

        assertThat(ctx(odd).source().orElseThrow().sourceId()).isEqualTo(other.sourceId());
        assertThat(ctx(odd).equipmentMethod()).isEqualTo("Flatbed scanner");
        assertThat(ctx(odd).overridden()).containsExactlyInAnyOrder("source", "equipment_method");
        assertThat(ctx(unknown).captureDate()).isNull();
        assertThat(ctx(unknown).blanked()).containsExactly("capture_date");
        for (ArchivalFile f : files) {
            if (f != odd && f != unknown) {
                assertThat(ctx(f).source().orElseThrow().sourceId()).as("unrelated Files unchanged").isEqualTo(book.sourceId());
                assertThat(ctx(f).equipmentMethod()).isEqualTo("Phone camera, copy stand");
                assertThat(ctx(f).captureDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            }
        }
        assertThat(sessions().get(rx.viewer, s.captureSessionId()).sourceId()).as("session unchanged").isEqualTo(book.sourceId());
    }

    /**
     * Correcting the session changes INHERIT rows only; VALUE and BLANK rows keep theirs. The correction is recorded
     * once, in the session's history, and never written into inheriting Files' history.
     */
    @Test
    void sessionCorrectionsResolveAtReadTimeWithoutRewritingFileHistory() {
        CaptureSession s = sessions().create(rx.editor, new CaptureSessionService.SessionInput("Visit", null, null,
                null, null, null, null));
        List<ArchivalFile> f = rx.files(3);
        sessions().addFiles(rx.editor, s.captureSessionId(), f.stream().map(ArchivalFile::fileId).toList());
        sessions().correctOverrides(rx.editor, f.get(1).fileId(), rx.row(f.get(1).fileId()).version(), new Overrides(
                FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.of("A. Helper"),
                FieldOverride.inherit()), null);
        sessions().correctOverrides(rx.editor, f.get(2).fileId(), rx.row(f.get(2).fileId()).version(), new Overrides(
                FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.blank(),
                FieldOverride.inherit()), null);
        int historyBefore = fileHistory(f.get(0)).size();

        CaptureSession cur = sessions().get(rx.editor, s.captureSessionId());
        sessions().correct(rx.editor, cur.captureSessionId(), cur.version(), new CaptureSessionService.SessionInput(
                "Visit", null, null, null, "R. Researcher", null, null), "researcher name added");

        assertThat(ctx(f.get(0)).researcher()).isEqualTo("R. Researcher");
        assertThat(ctx(f.get(1)).researcher()).isEqualTo("A. Helper");
        assertThat(ctx(f.get(2)).researcher()).as("explicitly not known stays not known").isNull();
        assertThat(fileHistory(f.get(0))).as("no File history from a session correction").hasSize(historyBefore);
        assertThat(rx.fx.services().history().forObject(rx.admin, "capture_session", s.captureSessionId()))
                .anySatisfy(h -> assertThat(h.fieldChanges()).anySatisfy(fc -> {
                    assertThat(fc.field()).isEqualTo("researcher");
                    assertThat(fc.after()).isEqualTo("R. Researcher");
                }));
    }

    /** Override history records mode and value separately, so "inherit", "not known" and a value never blur. */
    @Test
    void overrideHistoryDistinguishesModes() {
        CaptureSession s = rx.session("Visit");
        ArchivalFile f = rx.file();
        rx.add(s, List.of(f));
        sessions().correctOverrides(rx.editor, f.fileId(), rx.row(f.fileId()).version(), new Overrides(
                FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.blank(),
                FieldOverride.inherit()), null);
        sessions().correctOverrides(rx.editor, f.fileId(), rx.row(f.fileId()).version(), new Overrides(
                FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.of("not known"),
                FieldOverride.inherit()), null);
        sessions().correctOverrides(rx.editor, f.fileId(), rx.row(f.fileId()).version(), inheritAll(), null);
        List<FieldChange> changes = fileHistory(f).stream().filter(h -> h.changeKind().equals("CAPTURE_CONTEXT_CORRECTED"))
                .flatMap(h -> h.fieldChanges().stream()).toList();
        assertThat(changes).extracting(fc -> fc.field() + ":" + fc.before() + "->" + fc.after()).containsExactly(
                "capture_researcher_mode:INHERIT->BLANK",
                "capture_researcher_mode:BLANK->VALUE", "capture_researcher:null->not known",
                "capture_researcher_mode:VALUE->INHERIT", "capture_researcher:not known->null");
        assertThat(ctx(f).overridden()).isEmpty();
        assertThat(ctx(f).blanked()).isEmpty();
    }

    @Test
    void anOwnValueMustBePresent() {
        CaptureSession s = rx.session("Visit");
        ArchivalFile f = rx.file();
        rx.add(s, List.of(f));
        assertThatThrownBy(() -> sessions().correctOverrides(rx.editor, f.fileId(), rx.row(f.fileId()).version(),
                new Overrides(FieldOverride.inherit(), FieldOverride.inherit(), FieldOverride.inherit(),
                        FieldOverride.of("   "), FieldOverride.inherit()), null)).isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> new FieldOverride<>(OverrideMode.BLANK, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    /** BI-002: Files are valid archival Files without any session, Record or classification. */
    @Test
    void bi002_filesNeedNoInterpretation() {
        ArchivalFile f = rx.file();
        assertThat(sessions().context(rx.viewer, f.fileId())).isEmpty();
        assertThat(rx.records().records().usesOfFile(rx.viewer, f.fileId())).isEmpty();
        assertThat(rx.fx.file(f.fileId()).lifecycle().name()).isEqualTo("ACTIVE");
    }

    /** A File belongs to at most one session; adding by ranges reports what happened. */
    @Test
    void addingFilesByIdsAndRanges() {
        CaptureSession s = rx.session("One");
        CaptureSession t = rx.session("Two");
        List<ArchivalFile> f = rx.files(5);
        CaptureSessionService.AddResult r = sessions().addByFriendlyIds(rx.editor, s.captureSessionId(),
                f.get(0).friendlyId() + "-" + f.get(2).friendlyId() + ", " + f.get(4).friendlyId().toLowerCase());
        assertThat(r.added()).isEqualTo(4);
        CaptureSessionService.AddResult again = sessions().addByFriendlyIds(rx.editor, t.captureSessionId(),
                f.get(1).friendlyId() + " " + f.get(3).friendlyId());
        assertThat(again.added()).isEqualTo(1);
        assertThat(again.alreadyInASession()).isEqualTo(1);
        assertThat(sessions().worksheet(rx.viewer, s.captureSessionId(), 0, 50).rows())
                .extracting(w -> w.file().fileId())
                .containsExactly(f.get(0).fileId(), f.get(1).fileId(), f.get(2).fileId(), f.get(4).fileId());
        assertThatThrownBy(() -> sessions().addByFriendlyIds(rx.editor, s.captureSessionId(), "F00010-F00001"))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> sessions().addByFriendlyIds(rx.editor, s.captureSessionId(), "R00001"))
                .isInstanceOf(RuleViolationException.class);
        UUID unknown = UUID.randomUUID();
        assertThat(sessions().addFiles(rx.editor, s.captureSessionId(), List.of(unknown)).notAvailable()).isEqualTo(1);
    }
}
