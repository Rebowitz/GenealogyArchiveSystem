package org.genealogyarchive.records;

import org.genealogyarchive.access.AccessDeniedException;
import org.genealogyarchive.history.ChangeRecord;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.genealogyarchive.testsupport.RecordsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Archival Records, File links, Roles, Citations, vocabularies and the Record lifecycle (R-001...R-003, R-006,
 * A-003, O-003...O-006, D3-01, D3-05, D3-06, D3-08, D3-10, D3-12; plan sections 10.1 and 10.5).
 */
class RecordsIT {

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

    private ArchivalRecord newRecord(String classification) {
        return records().create(rx.editor, new RecordService.RecordInput(classification, null, null, null, null, null,
                null, null, null));
    }

    private ArchivalRecord current(ArchivalRecord r) {
        return records().get(rx.admin, r.recordId());
    }

    private List<ChangeRecord> history(ArchivalRecord r) {
        return records().history(rx.admin, r.recordId(), rx.fx.services().history());
    }

    // ---- identity, incompleteness, files -------------------------------------------------------------

    /** A-003, D3-07: a Record needs nothing beyond its identity, and gets an R friendly ID. */
    @Test
    void anIncompleteRecordIsValid() {
        ArchivalRecord r = newRecord(null);
        assertThat(r.friendlyId()).matches("R\\d{5,}");
        assertThat(r.classificationCode()).isNull();
        assertThat(r.sourceId()).isNull();
        assertThat(records().getByFriendlyId(rx.viewer, r.friendlyId().toLowerCase()).recordId()).isEqualTo(r.recordId());
    }

    /** R-001, R-002, R-003: pages 227-229 with two captures of 227; one photograph shared by two deeds. */
    @Test
    void r001_r002_r003_filesAndPages() {
        List<ArchivalFile> f = rx.files(4);
        ArchivalRecord deedA = newRecord("DEED");
        ArchivalRecord deedB = newRecord("DEED");
        deedA = linkAll(deedA, List.of(f.get(0), f.get(1), f.get(2), f.get(3)), List.of("227", "227", "228", "229"));
        // R-003: the last photograph also shows the beginning of deed B.
        deedB = current(deedB);
        records().linkFile(rx.editor, deedB.recordId(), deedB.version(), f.get(3).fileId(), "229", "top of page");

        List<RecordService.LinkedFile> a = records().view(rx.viewer, deedA.recordId()).files();
        assertThat(a).extracting(l -> l.file().fileId()).containsExactly(f.get(0).fileId(), f.get(1).fileId(),
                f.get(2).fileId(), f.get(3).fileId());
        assertThat(a).extracting(l -> l.link().pageDesignation()).containsExactly("227", "227", "228", "229");
        assertThat(records().usesOfFile(rx.viewer, f.get(3).fileId())).extracting(u -> u.record().recordId())
                .containsExactlyInAnyOrder(deedA.recordId(), deedB.recordId());
        // Each File keeps its own identity.
        assertThat(a).extracting(l -> l.file().friendlyId()).doesNotHaveDuplicates();
        // A File is linked to a Record at most once.
        ArchivalRecord fresh = current(deedA);
        assertThatThrownBy(() -> records().linkFile(rx.editor, fresh.recordId(), fresh.version(), f.get(0).fileId(), null, null))
                .isInstanceOf(RuleViolationException.class);
    }

    private ArchivalRecord linkAll(ArchivalRecord r, List<ArchivalFile> files, List<String> pages) {
        for (int i = 0; i < files.size(); i++) {
            ArchivalRecord c = current(r);
            records().linkFile(rx.editor, c.recordId(), c.version(), files.get(i).fileId(), pages.get(i), null);
        }
        return current(r);
    }

    /** R-001: re-paging and reordering never couple page to image order. */
    @Test
    void pagesAndImageOrderAreIndependent() {
        List<ArchivalFile> f = rx.files(3);
        ArchivalRecord r = linkAll(newRecord(null), f, List.of("12", "13", "14"));
        records().moveLink(rx.editor, r.recordId(), r.version(), f.get(2).fileId(), true);
        r = current(r);
        records().correctLink(rx.editor, r.recordId(), r.version(), f.get(0).fileId(), "12A", "faint");
        List<RecordService.LinkedFile> view = records().view(rx.viewer, r.recordId()).files();
        assertThat(view).extracting(l -> l.file().fileId()).containsExactly(f.get(0).fileId(), f.get(2).fileId(), f.get(1).fileId());
        assertThat(view).extracting(l -> l.link().pageDesignation()).containsExactly("12A", "14", "13");
    }

    /** R-006: DEED corrected to DTRUST; Record ID, File IDs, names and stored originals unchanged. */
    @Test
    void r006_classificationCorrection() throws IOException {
        List<ArchivalFile> f = rx.files(2);
        // The acceptance scenario's own identifier: the next Record is R01842.
        JdbcClient.create(rx.fx.ds).sql("SELECT setval('gas.friendly_id_seq_archival_record', 1841)").query(Long.class).single();
        ArchivalRecord r = linkAll(newRecord("DEED"), f, List.of("1", "2"));
        assertThat(r.friendlyId()).isEqualTo("R01842");
        byte[] before = Files.readAllBytes(rx.fx.storedPath(f.get(0)));
        Path pathBefore = rx.fx.storedPath(f.get(0));
        ArchivalRecord c = current(r);
        Citation cite = rx.records().citations().create(rx.editor, null, r.recordId(), "p. 1", "Deed, Book 12", null);

        records().correct(rx.editor, c.recordId(), c.version(), new RecordService.RecordInput("DTRUST", null, null, null,
                null, null, null, null, null), "misread on first pass");

        ArchivalRecord after = current(r);
        assertThat(after.recordId()).isEqualTo(r.recordId());
        assertThat(after.friendlyId()).isEqualTo(r.friendlyId());
        assertThat(after.classificationCode()).isEqualTo("DTRUST");
        assertThat(records().view(rx.viewer, r.recordId()).files()).extracting(l -> l.file().fileId())
                .containsExactly(f.get(0).fileId(), f.get(1).fileId());
        ArchivalFile fileAfter = rx.fx.file(f.get(0).fileId());
        assertThat(fileAfter.managedFilename()).isEqualTo(f.get(0).managedFilename());
        assertThat(rx.fx.storedPath(fileAfter)).isEqualTo(pathBefore);
        assertThat(Files.readAllBytes(pathBefore)).isEqualTo(before);
        // Plan 10.1: reclassifying the cited Record changes nothing about the Citation.
        assertThat(rx.records().citations().get(rx.viewer, cite.citationId())).isEqualTo(cite);
        assertThat(history(r)).anySatisfy(h -> assertThat(h.fieldChanges()).anySatisfy(fc -> {
            assertThat(fc.field()).isEqualTo("classification");
            assertThat(fc.before()).isEqualTo("DEED");
            assertThat(fc.after()).isEqualTo("DTRUST");
        }));
    }

    /** D3-05, D3-10: dates as written; transcription and abstract are separate, attributed fields. */
    @Test
    void datesAsWrittenAndSeparateTranscriptionAndAbstract() {
        Source src = rx.records().catalog().createSource(rx.editor, new org.genealogyarchive.records.CatalogService.SourceInput(
                "Deed Book 12", null, "Deed book", "Book 12", null, null, null, null));
        ArchivalRecord r = records().create(rx.editor, new RecordService.RecordInput("DEED", "Smith to Jones", src.sourceId(),
                "Book 12, p. 227", "the 3d day of March in the year 1803", "Recd. 10 Apr 1803",
                "This Indenture made the 3d day of March...", "Sale of 100 acres on Mill Creek.", null));
        assertThat(r.recordDateText()).isEqualTo("the 3d day of March in the year 1803");
        assertThat(r.filingDateText()).isEqualTo("Recd. 10 Apr 1803");
        assertThat(r.transcriptionText()).isEqualTo("This Indenture made the 3d day of March...");
        assertThat(r.abstractText()).isEqualTo("Sale of 100 acres on Mill Creek.");
        records().correct(rx.editor, r.recordId(), r.version(), new RecordService.RecordInput("DEED", "Smith to Jones",
                src.sourceId(), "Book 12, p. 227", r.recordDateText(), r.filingDateText(), r.transcriptionText(),
                "Sale of 100 acres on Mill Creek, Augusta Co.", null), null);
        List<FieldChange> changes = history(r).stream().flatMap(h -> h.fieldChanges().stream()).toList();
        assertThat(changes).anySatisfy(fc -> {
            assertThat(fc.field()).isEqualTo("abstract");
            assertThat(fc.before()).isEqualTo("Sale of 100 acres on Mill Creek.");
        });
        assertThat(changes).noneMatch(fc -> fc.field().equals("transcription_verbatim") && fc.before() != null);
    }

    /** D3-01: Roles keep the party as written (or none, if unreadable) and create no Persons. */
    @Test
    void rolesArePartiesAsWritten() {
        ArchivalRecord r = newRecord("DEED");
        RecordRole grantor = records().addRole(rx.editor, r.recordId(), r.version(), "grantor", "Jno. Smith", null);
        r = current(r);
        records().addRole(rx.editor, r.recordId(), r.version(), "WITNESS", null, "signature illegible");
        assertThat(grantor.roleCode()).isEqualTo("GRANTOR");
        assertThat(records().view(rx.viewer, r.recordId()).roles()).extracting(RecordRole::partyAsWritten)
                .containsExactly("Jno. Smith", null);
        ArchivalRecord c = current(r);
        assertThatThrownBy(() -> records().addRole(rx.editor, c.recordId(), c.version(), "NO_SUCH_ROLE", "x", null))
                .isInstanceOf(RuntimeException.class);
        assertThat(JdbcClient.create(rx.fx.ds).sql("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'gas' "
                + "AND table_name IN ('person', 'person_relationship', 'event')").query(Long.class).single()).isZero();
    }

    /** D3-08: retired terms stay valid where used but are not offered; codes are never reused. */
    @Test
    void vocabularyTermsAreRetiredNeverDeleted() {
        ArchivalRecord r = newRecord("DEED");
        VocabularyTerm deed = rx.records().vocabularies().terms(rx.admin, Vocabulary.CLASSIFICATION).stream()
                .filter(t -> t.code().equals("DEED")).findFirst().orElseThrow();
        rx.records().vocabularies().correct(rx.admin, Vocabulary.CLASSIFICATION, "DEED", deed.version(), "Deed",
                null, true);
        assertThat(rx.records().vocabularies().active(rx.admin, Vocabulary.CLASSIFICATION)).extracting(VocabularyTerm::code)
                .doesNotContain("DEED");
        assertThat(current(r).classificationCode()).isEqualTo("DEED");
        // Still valid on the Record that has it: correcting another field keeps it.
        ArchivalRecord c = current(r);
        records().correct(rx.editor, c.recordId(), c.version(), new RecordService.RecordInput("DEED", "kept", null, null,
                null, null, null, null, null), null);
        // Not offered for new use.
        assertThatThrownBy(() -> newRecord("DEED")).isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> rx.records().vocabularies().add(rx.admin, Vocabulary.CLASSIFICATION, "deed", "Again", null))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> rx.records().vocabularies().add(rx.editor, Vocabulary.CLASSIFICATION, "WILL", "Will", null))
                .isInstanceOf(AccessDeniedException.class);
        rx.records().vocabularies().add(rx.admin, Vocabulary.CLASSIFICATION, "will", "Will", null);
        assertThat(newRecord("WILL").classificationCode()).isEqualTo("WILL");
    }

    // ---- Repositories and Sources (O-003...O-006) ------------------------------------------------

    @Test
    void o003_to_o006_repositoryCreatorAccessProviderAndJurisdictionStayDistinct() {
        // O-003: present custodian unknown.
        Source unknownCustody = rx.records().catalog().createSource(rx.editor, new CatalogService.SourceInput(
                "Order Book 3", null, null, null, null, null, null, null));
        assertThat(unknownCustody.repositoryId()).isNull();
        ArchivalRecord r = records().create(rx.editor, new RecordService.RecordInput(null, null, unknownCustody.sourceId(),
                null, null, null, null, null, null));
        assertThat(r.sourceId()).isEqualTo(unknownCustody.sourceId());
        // O-004/O-005/O-006: creator, Repository, Access Provider and jurisdiction are separate.
        Repository archive = rx.records().catalog().createRepository(rx.editor, "State Archives (invented)", null, "Capital City");
        Source s = rx.records().catalog().createSource(rx.editor, new CatalogService.SourceInput("Deed Book 12",
                archive.repositoryId(), "Deed book", "Book 12", "County Court of Example County",
                "Online image service (invented)", "Example County", null));
        assertThat(s.creatorText()).isEqualTo("County Court of Example County");
        assertThat(s.accessProviderText()).isEqualTo("Online image service (invented)");
        assertThat(s.jurisdictionText()).isEqualTo("Example County");
        Repository stored = rx.records().catalog().repository(rx.viewer, archive.repositoryId());
        assertThat(stored.name()).isEqualTo("State Archives (invented)");
        assertThat(List.of(stored.name(), String.valueOf(stored.description()), String.valueOf(stored.locationText())))
                .noneMatch(v -> v.contains("County Court") || v.contains("image service"));
        assertThat(rx.records().catalog().repositoryByFriendlyId(rx.viewer, archive.friendlyId()).repositoryId())
                .isEqualTo(archive.repositoryId());
        assertThat(rx.records().catalog().sourceByFriendlyId(rx.viewer, s.friendlyId()).sourceId()).isEqualTo(s.sourceId());
    }

    // ---- Citations (plan 10.1) ----------------------------------------------------------------------

    @Test
    void citationIdentityAndProvenance() {
        Source s = rx.records().catalog().createSource(rx.editor, new CatalogService.SourceInput("Deed Book 12", null,
                null, null, null, null, null, null));
        ArchivalRecord r = records().create(rx.editor, new RecordService.RecordInput(null, null, s.sourceId(), null, null,
                null, null, null, null));
        ArchivalRecord noSource = newRecord(null);
        Citation sourceOnly = rx.records().citations().create(rx.editor, s.sourceId(), null, "p. 227", null, null);
        Citation recordOnly = rx.records().citations().create(rx.editor, null, noSource.recordId(), "entry 4", null, null);
        Citation both = rx.records().citations().create(rx.editor, s.sourceId(), r.recordId(), "p. 228", "Book 12:228", null);
        assertThat(sourceOnly.friendlyId()).matches("CT\\d{5,}");
        // A Record-only Citation stores no Source; nothing is inferred.
        assertThat(recordOnly.sourceId()).isNull();
        // Correcting the locator keeps the original as first entered, the identity and the attribution.
        Citation corrected = rx.records().citations().correct(rx.admin, both.citationId(), both.version(), "pp. 228-229",
                "Book 12:228-229", null, false);
        assertThat(corrected.citationId()).isEqualTo(both.citationId());
        assertThat(corrected.friendlyId()).isEqualTo(both.friendlyId());
        assertThat(corrected.originalLocator()).isEqualTo("p. 228");
        assertThat(corrected.locator()).isEqualTo("pp. 228-229");
        assertThat(corrected.createdBy()).isEqualTo(rx.editor.userId());
        assertThat(corrected.createdAt()).isEqualTo(both.createdAt());
        assertThat(corrected.sourceId()).isEqualTo(s.sourceId());
        assertThat(corrected.recordId()).isEqualTo(r.recordId());
        assertThat(rx.records().citations().history(rx.admin, both.citationId(), rx.fx.services().history()))
                .anySatisfy(h -> assertThat(h.fieldChanges()).anySatisfy(fc -> {
                    assertThat(fc.field()).isEqualTo("locator");
                    assertThat(fc.before()).isEqualTo("p. 228");
                }));
        assertThatThrownBy(() -> rx.records().citations().create(rx.editor, null, null, "x", null, null))
                .isInstanceOf(RuleViolationException.class);
        // Deleting is recoverable; the Citation stays reachable.
        Citation deleted = rx.records().citations().correct(rx.editor, sourceOnly.citationId(), sourceOnly.version(),
                "p. 227", null, null, true);
        assertThat(deleted.lifecycle()).isEqualTo(RecordLifecycle.DELETED);
        assertThat(rx.records().citations().get(rx.viewer, sourceOnly.citationId()).lifecycle()).isEqualTo(RecordLifecycle.DELETED);
    }

    // ---- lifecycle (D3-12, plan 10.5) ---------------------------------------------------------------

    @Test
    void deletionIsRecoverableAndPreservesEverything() {
        List<ArchivalFile> f = rx.files(2);
        ArchivalRecord r = linkAll(newRecord("DEED"), f, List.of("1", "2"));
        r = current(r);
        records().addRole(rx.editor, r.recordId(), r.version(), "GRANTOR", "A. Smith", null);
        Citation c = rx.records().citations().create(rx.editor, null, r.recordId(), "p. 1", null, null);
        ArchivalRecord before = current(r);

        ArchivalRecord deleted = records().delete(rx.editor, before.recordId(), before.version(), "entered twice");
        assertThat(deleted.lifecycle()).isEqualTo(RecordLifecycle.DELETED);
        // Still reachable by ID, marked deleted; links, roles, citation and history kept.
        RecordService.RecordView view = records().view(rx.viewer, r.recordId());
        assertThat(view.files()).hasSize(2);
        assertThat(view.roles()).hasSize(1);
        assertThat(rx.records().citations().get(rx.viewer, c.citationId()).recordId()).isEqualTo(r.recordId());
        assertThat(records().usesOfFile(rx.viewer, f.get(0).fileId())).extracting(u -> u.record().lifecycle())
                .containsExactly(RecordLifecycle.DELETED);
        // Excluded from default lists; included on request for editors only.
        assertThat(records().list(rx.viewer, false, null, null, null, 100)).extracting(ArchivalRecord::recordId)
                .doesNotContain(r.recordId());
        assertThat(records().list(rx.viewer, true, null, null, null, 100)).extracting(ArchivalRecord::recordId)
                .doesNotContain(r.recordId());
        assertThat(records().list(rx.editor, true, null, null, null, 100)).extracting(ArchivalRecord::recordId)
                .contains(r.recordId());
        // Nothing changes while deleted except restore.
        ArchivalRecord d = current(r);
        assertThatThrownBy(() -> records().correct(rx.editor, d.recordId(), d.version(), new RecordService.RecordInput(
                "DTRUST", null, null, null, null, null, null, null, null), null)).isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> records().linkFile(rx.editor, d.recordId(), d.version(), rx.file().fileId(), null, null))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> records().unlinkFile(rx.editor, d.recordId(), d.version(), f.get(0).fileId(), null))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> records().addRole(rx.editor, d.recordId(), d.version(), "WITNESS", "x", null))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> rx.records().citations().create(rx.editor, null, d.recordId(), "p. 2", null, null))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> records().delete(rx.editor, d.recordId(), d.version(), null))
                .isInstanceOf(RuleViolationException.class);
        assertThatThrownBy(() -> records().restore(rx.viewer, d.recordId(), d.version(), null))
                .isInstanceOf(AccessDeniedException.class);

        ArchivalRecord restored = records().restore(rx.editor, d.recordId(), d.version(), "not a duplicate after all");
        assertThat(restored.lifecycle()).isEqualTo(RecordLifecycle.ACTIVE);
        assertThat(restored.friendlyId()).isEqualTo(r.friendlyId());
        assertThat(records().view(rx.viewer, r.recordId()).files()).hasSize(2);
        assertThat(history(r)).extracting(ChangeRecord::changeKind).contains("DELETED", "RESTORED");
        // Identifiers are never reused.
        ArchivalRecord next = newRecord(null);
        assertThat(next.friendlyId()).isNotEqualTo(r.friendlyId());
    }

    /** Authorization: Viewers read; only Editors and Administrators change Records. */
    @Test
    void viewersCannotChangeRecords() {
        ArchivalRecord r = newRecord(null);
        assertThatThrownBy(() -> newRecordAs(rx.viewer)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> records().correct(rx.viewer, r.recordId(), r.version(), new RecordService.RecordInput(
                null, "x", null, null, null, null, null, null, null), null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> rx.records().citations().create(rx.viewer, null, r.recordId(), null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> rx.records().catalog().createRepository(rx.viewer, "x", null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> rx.records().grouping().apply(rx.viewer, rx.session("y").captureSessionId()))
                .isInstanceOf(AccessDeniedException.class);
        // An Editor cannot restrict a Record (D3-04: Administrators only).
        assertThatThrownBy(() -> records().correct(rx.editor, r.recordId(), r.version(), new RecordService.RecordInput(
                null, null, null, null, null, null, null, null, Restriction.RESTRICTED), null))
                .isInstanceOf(AccessDeniedException.class);
        // Optimistic concurrency.
        records().correct(rx.editor, r.recordId(), r.version(), new RecordService.RecordInput(null, "first", null, null,
                null, null, null, null, null), null);
        assertThatThrownBy(() -> records().correct(rx.editor, r.recordId(), r.version(), new RecordService.RecordInput(
                null, "second", null, null, null, null, null, null, null), null))
                .isInstanceOf(org.genealogyarchive.service.ConflictException.class);
        assertThatThrownBy(() -> records().get(rx.viewer, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    private ArchivalRecord newRecordAs(org.genealogyarchive.access.Actor actor) {
        return records().create(actor, new RecordService.RecordInput(null, null, null, null, null, null, null, null, null));
    }
}
