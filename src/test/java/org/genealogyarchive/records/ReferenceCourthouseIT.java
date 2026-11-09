package org.genealogyarchive.records;

import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.testsupport.RecordsFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The synthetic courthouse deed book of the Reference Acceptance Archive (BUILD-SPEC s.64;
 * {@code reference-archive/courthouse-deed-book.yaml}), built through the ordinary services exactly as a researcher
 * would, and checked against the outcome the fixture states: R-001...R-006, O-003...O-006 (Phase 3 parts), BI-001,
 * F-005 (captures grouped for viewing, none consolidated), EV-003 (capture date distinct from record dates).
 */
class ReferenceCourthouseIT {

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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fixture() throws IOException {
        try (InputStream in = ReferenceCourthouseIT.class.getResourceAsStream("/reference-archive/courthouse-deed-book.yaml")) {
            assertThat(in).as("fixture on the test classpath").isNotNull();
            return (Map<String, Object>) new Yaml(new SafeConstructor(new LoaderOptions()))
                    .load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    @Test
    @SuppressWarnings("unchecked")
    void r001ToR006_syntheticDeedBook() throws IOException {
        Map<String, Object> fx = fixture();
        Map<String, Object> repo = map(fx.get("repository"));
        Map<String, Object> src = map(fx.get("source"));
        Map<String, Object> ses = map(fx.get("session"));

        Repository repository = rx.records().catalog().createRepository(rx.editor, str(repo, "name"), null, str(repo, "location"));
        Source source = rx.records().catalog().createSource(rx.editor, new CatalogService.SourceInput(str(src, "title"),
                repository.repositoryId(), str(src, "type"), str(src, "volume"), str(src, "creator"),
                str(src, "accessProvider"), str(src, "jurisdiction"), null));
        LocalDate captureDate = LocalDate.parse(str(ses, "captureDate"));
        CaptureSession session = rx.records().sessions().create(rx.editor, new CaptureSessionService.SessionInput(
                str(ses, "title"), repository.repositoryId(), source.sourceId(), captureDate, str(ses, "researcher"),
                str(ses, "method"), null));

        // Ingest the images (synthetic bytes) and add them in capture order.
        Map<String, ArchivalFile> files = new LinkedHashMap<>();
        Map<String, byte[]> bytes = new HashMap<>();
        for (Object o : (List<Object>) fx.get("images")) {
            String key = str(map(o), "key");
            byte[] b = ("synthetic courthouse image " + key).getBytes(StandardCharsets.UTF_8);
            files.put(key, rx.fx.ingest(rx.editor, key + ".jpg", b));
            bytes.put(key, b);
        }
        rx.records().sessions().addFiles(rx.editor, session.captureSessionId(),
                files.values().stream().map(ArchivalFile::fileId).toList());

        // BI-001: every image inherits the session's context without re-entry.
        for (ArchivalFile f : files.values()) {
            CaptureSessionService.EffectiveContext c = rx.records().sessions().context(rx.viewer, f.fileId()).orElseThrow();
            assertThat(c.source().orElseThrow().sourceId()).isEqualTo(source.sourceId());
            assertThat(c.captureDate()).isEqualTo(captureDate);
        }

        // The researcher's worksheet marks, saved as one page.
        List<CaptureSessionService.MarkEdit> marks = new ArrayList<>();
        for (Object o : (List<Object>) fx.get("images")) {
            Map<String, Object> img = map(o);
            ArchivalFile f = files.get(str(img, "key"));
            marks.add(new CaptureSessionService.MarkEdit(f.fileId(), rx.row(f.fileId()).version(),
                    Boolean.TRUE.equals(img.get("start")), str(img, "group"), str(img, "page"), str(img, "class")));
        }
        rx.records().sessions().saveMarks(rx.editor, session.captureSessionId(), marks);

        // R-005: apply only through the last image the researcher finished.
        GroupingRun run = rx.records().grouping().apply(rx.editor, session.captureSessionId(),
                files.get(str(fx, "applyThrough")).fileId());
        assertThat(run.state()).isEqualTo(GroupingRun.State.COMPLETED);

        Map<String, Object> expected = map(fx.get("expected"));
        Map<String, UUID> recordIds = new HashMap<>();
        for (Map.Entry<String, Object> e : map(expected.get("records")).entrySet()) {
            recordIds.put(e.getKey(), rx.row(files.get(str(map(e.getValue()), "first")).fileId()).appliedRecordId());
        }
        assertThat(recordIds.values()).doesNotContainNull().doesNotHaveDuplicates();

        // Links made by hand (R-003: one photograph shows the end of Deed A and the start of Deed B).
        for (Object o : (List<Object>) fx.get("extraLinks")) {
            Map<String, Object> l = map(o);
            ArchivalRecord r = rx.records().records().get(rx.editor, recordIds.get(str(l, "record")));
            rx.records().records().linkFile(rx.editor, r.recordId(), r.version(), files.get(str(l, "image")).fileId(),
                    str(l, "page"), str(l, "note"));
        }

        // R-001, R-002, R-003, R-004: each expected Record, its images in order and its pages as in the source.
        for (Map.Entry<String, Object> e : map(expected.get("records")).entrySet()) {
            Map<String, Object> want = map(e.getValue());
            RecordService.RecordView view = rx.records().records().view(rx.viewer, recordIds.get(e.getKey()));
            assertThat(view.record().classificationCode()).as(e.getKey()).isEqualTo(str(want, "class"));
            assertThat(view.files()).as(e.getKey()).extracting(l -> l.file().fileId()).containsExactlyElementsOf(
                    ((List<Object>) want.get("images")).stream().map(k -> files.get(k.toString()).fileId()).toList());
            assertThat(view.files()).as(e.getKey()).extracting(l -> l.link().pageDesignation())
                    .containsExactlyElementsOf(((List<Object>) want.get("pages")).stream().map(Object::toString).toList());
            assertThat(view.source().orElseThrow().sourceId()).isEqualTo(source.sourceId());
            assertThat(view.record().recordDateText()).as("EV-003: no record date invented from the capture date").isNull();
        }
        // R-005: the unfinished images remain valid, ungrouped archived material.
        for (Object k : (List<Object>) expected.get("unapplied")) {
            ArchivalFile f = files.get(k.toString());
            assertThat(rx.row(f.fileId()).applied()).as(k.toString()).isFalse();
            assertThat(rx.fx.file(f.fileId()).lifecycle().name()).isEqualTo("ACTIVE");
        }
        // F-005: two captures of page 227 coexist as distinct Files; nothing was consolidated.
        assertThat(files.get("img01").fileId()).isNotEqualTo(files.get("img02").fileId());
        assertThat(rx.fx.file(files.get("img02").fileId()).lifecycle().name()).isEqualTo("ACTIVE");

        // R-006: reclassify; identities, links, names and stored originals unchanged.
        Map<String, Object> rc = map(expected.get("reclassify"));
        UUID target = recordIds.get(str(rc, "record"));
        ArchivalRecord before = rx.records().records().get(rx.editor, target);
        List<UUID> linkedBefore = rx.records().records().view(rx.viewer, target).files().stream().map(l -> l.file().fileId()).toList();
        rx.records().records().correct(rx.editor, target, before.version(), new RecordService.RecordInput(str(rc, "to"),
                before.summary(), before.sourceId(), before.sourceLocator(), before.recordDateText(), before.filingDateText(),
                before.transcriptionText(), before.abstractText(), null), "reclassified");
        ArchivalRecord after = rx.records().records().get(rx.editor, target);
        assertThat(after.friendlyId()).isEqualTo(before.friendlyId());
        assertThat(after.classificationCode()).isEqualTo(str(rc, "to"));
        assertThat(rx.records().records().view(rx.viewer, target).files()).extracting(l -> l.file().fileId())
                .containsExactlyElementsOf(linkedBefore);
        for (Map.Entry<String, ArchivalFile> e : files.entrySet()) {
            ArchivalFile now = rx.fx.file(e.getValue().fileId());
            assertThat(now.managedFilename()).isEqualTo(e.getValue().managedFilename());
            assertThat(rx.fx.storedPath(now)).isEqualTo(rx.fx.storedPath(e.getValue()));
            assertThat(Files.readAllBytes(rx.fx.storedPath(now))).isEqualTo(bytes.get(e.getKey()));
        }

        // O-004...O-006: creator, Access Provider and jurisdiction stay on the Source, apart from the Repository.
        Source s = rx.records().catalog().source(rx.viewer, source.sourceId());
        assertThat(s.creatorText()).isEqualTo(str(src, "creator"));
        assertThat(s.accessProviderText()).isEqualTo(str(src, "accessProvider"));
        assertThat(s.jurisdictionText()).isEqualTo(str(src, "jurisdiction"));
        assertThat(rx.records().catalog().repository(rx.viewer, repository.repositoryId()).name()).isEqualTo(str(repo, "name"));
    }
}
