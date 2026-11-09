package org.genealogyarchive.web;

import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.config.InstallationConfig.FileSettings;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.records.ArchivalRecord;
import org.genealogyarchive.records.CaptureRow;
import org.genealogyarchive.records.RecordService;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.RecordsFixture;
import org.genealogyarchive.testsupport.TestDatabase;
import org.genealogyarchive.testsupport.WebSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 pages through the real web process: every page renders for each role, the courthouse workflow works in
 * the browser (create catalogue entries, add Files, mark the worksheet, apply, open the Record), and restricted
 * Records and Files stay invisible over HTTP (D3-04).
 */
class RecordsWebIT {

    @TempDir
    Path temp;
    RecordsFixture rx;
    ConfigurableApplicationContext app;

    @BeforeEach
    void setUp() throws IOException {
        rx = RecordsFixture.create(temp);
        InstallationConfig base = TestDatabase.installationConfig(rx.fx.db.settings());
        InstallationConfig config = new InstallationConfig(base.sourceFile(), base.database(), base.server(), base.logging(),
                Map.of(rx.fx.volumeId, rx.fx.volumeRoot),
                new FileSettings(Optional.of(rx.fx.staging), Optional.of(rx.fx.cache), FileSettings.DEFAULT_MAX_UPLOAD_BYTES,
                        Duration.ofMinutes(15)));
        app = ProcessLauncher.startWeb(config, Map.of());
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.close();
        }
        rx.close();
    }

    private WebSession signedIn(String user) {
        WebSession s = new WebSession(app);
        assertThat(WebSession.location(s.signIn(user, ArchiveFixtures.PASSWORD))).doesNotContain("error");
        return s;
    }

    private static UUID created(HttpResponse<String> r, String path) {
        assertThat(r.statusCode()).as(r.body()).isEqualTo(302);
        Matcher m = Pattern.compile("/" + path + "/([0-9a-f-]{36})").matcher(WebSession.location(r));
        assertThat(m.find()).as(WebSession.location(r)).isTrue();
        return UUID.fromString(m.group(1));
    }

    private static void ok(WebSession s, String path, String... contains) {
        HttpResponse<String> r = s.get(path);
        assertThat(r.statusCode()).as(path + "\n" + r.body()).isEqualTo(200);
        if (contains.length > 0) {
            assertThat(r.body()).contains(contains);
        }
    }

    @Test
    void courthouseWorkflowInTheBrowser() {
        WebSession editor = signedIn("editor1");
        UUID repo = created(editor.submit("/repositories", "/repositories", Map.of("name", "County Clerk (invented)",
                "location", "Example Courthouse")), "repositories");
        UUID source = created(editor.submit("/sources", "/sources", Map.of("title", "Deed Book 12",
                "repositoryId", repo.toString(), "creatorText", "County Court (invented)",
                "accessProviderText", "Image service (invented)", "jurisdictionText", "Example County")), "sources");
        UUID session = created(editor.submit("/sessions", "/sessions", Map.of("title", "October visit",
                "repositoryId", repo.toString(), "sourceId", source.toString(), "captureDate", "2026-10-01",
                "researcher", "R. Researcher")), "sessions");
        ArchivalFile a = rx.file();
        ArchivalFile b = rx.file();
        HttpResponse<String> add = editor.submit("/sessions/" + session, "/sessions/" + session + "/files",
                Map.of("fileIds", a.friendlyId() + ", " + b.friendlyId()));
        assertThat(add.statusCode()).isEqualTo(302);
        ok(editor, "/sessions/" + session, a.friendlyId(), b.friendlyId(), "2 added");

        CaptureRow row = rx.row(a.fileId());
        Map<String, String> marks = new LinkedHashMap<>();
        marks.put("row", a.fileId().toString());
        marks.put("version_" + a.fileId(), String.valueOf(row.version()));
        marks.put("start", a.fileId().toString());
        marks.put("group_" + a.fileId(), "");
        marks.put("page_" + a.fileId(), "227");
        marks.put("class_" + a.fileId(), "DEED");
        assertThat(editor.submit("/sessions/" + session, "/sessions/" + session + "/worksheet", marks).statusCode())
                .isEqualTo(302);
        assertThat(editor.submit("/sessions/" + session, "/sessions/" + session + "/apply", Map.of()).statusCode())
                .isEqualTo(302);
        ok(editor, "/sessions/" + session, "Grouping applied", "COMPLETED");

        UUID record = rx.row(a.fileId()).appliedRecordId();
        assertThat(rx.row(b.fileId()).appliedRecordId()).isEqualTo(record);
        ok(editor, "/records/" + record, "Deed", a.friendlyId(), b.friendlyId(), "227", "Deed Book 12",
                "Verbatim transcription", "Abstract");
        ok(editor, "/files/" + a.fileId(), "Used in Records", "Capture context", "R. Researcher", "from CS");
        ok(editor, "/sources/" + source, "County Court (invented)", "Image service (invented)", "Example County");
        ok(editor, "/repositories/" + repo, "County Clerk (invented)", "Deed Book 12");

        // Correct the Record and add a role and a citation through the browser.
        ArchivalRecord r = rx.records().records().get(rx.admin, record);
        assertThat(editor.submit("/records/" + record, "/records/" + record + "/roles", Map.of("version",
                String.valueOf(r.version()), "roleCode", "GRANTOR", "party", "Jno. Smith")).statusCode()).isEqualTo(302);
        UUID citation = created(editor.submit("/records/" + record, "/citations", Map.of("recordId", record.toString(),
                "locator", "p. 227", "citationText", "Deed Book 12, p. 227")), "citations");
        ok(editor, "/citations/" + citation, "p. 227", "via the Record", "Deed Book 12");
        ok(editor, "/records/" + record, "Jno. Smith");

        // Every list page renders for every role.
        for (String user : new String[]{"admin1", "editor1", "viewer1"}) {
            WebSession s = user.equals("editor1") ? editor : signedIn(user);
            for (String page : new String[]{"/records", "/records?deleted=true", "/sources", "/repositories", "/sessions",
                    "/records/" + record, "/sessions/" + session, "/sources/" + source, "/repositories/" + repo,
                    "/citations/" + citation, "/files/" + a.fileId()}) {
                ok(s, page);
            }
        }
        ok(signedIn("admin1"), "/vocabularies", "DEED", "GRANTOR", "ADJOINING_LANDHOLDER");
        assertThat(editor.get("/vocabularies").statusCode()).isEqualTo(403);
        // Viewers see no editing forms.
        assertThat(signedIn("viewer1").get("/records/" + record).body()).doesNotContain("Add role", "Link File");
    }

    @Test
    void restrictedRecordsAndFilesStayInvisibleOverHttp() {
        ArchivalRecord hidden = rx.records().records().create(rx.admin, new RecordService.RecordInput("DEED",
                "restricted summary (invented)", null, null, null, null, null, null, Restriction.RESTRICTED));
        ArchivalFile open = rx.file();
        ArchivalFile secret = rx.restrictedFile();
        ArchivalRecord normal = rx.records().records().create(rx.admin, new RecordService.RecordInput(null, null, null,
                null, null, null, null, null, null));
        rx.records().records().linkFile(rx.admin, normal.recordId(), normal.version(), open.fileId(), "1", null);
        ArchivalRecord n2 = rx.records().records().get(rx.admin, normal.recordId());
        rx.records().records().linkFile(rx.admin, n2.recordId(), n2.version(), secret.fileId(), "SECRET-PAGE", null);

        for (String user : new String[]{"editor1", "viewer1"}) {
            WebSession s = signedIn(user);
            HttpResponse<String> hiddenPage = s.get("/records/" + hidden.recordId());
            HttpResponse<String> missingPage = s.get("/records/" + UUID.randomUUID());
            assertThat(hiddenPage.statusCode()).isEqualTo(404).isEqualTo(missingPage.statusCode());
            assertThat(hiddenPage.body()).doesNotContain("restricted summary");
            assertThat(s.get("/records/find?id=" + hidden.friendlyId()).statusCode()).isEqualTo(404);
            // The friendly ID text itself can appear as a form placeholder; the Record's link and UUID must not.
            assertThat(s.get("/records").body()).doesNotContain(">" + hidden.friendlyId() + "</a>",
                    hidden.recordId().toString());
            String page = s.get("/records/" + normal.recordId()).body();
            assertThat(page).contains(open.friendlyId()).doesNotContain(secret.friendlyId(), "SECRET-PAGE",
                    secret.fileId().toString());
        }
        WebSession admin = signedIn("admin1");
        ok(admin, "/records/" + hidden.recordId(), "restricted summary");
        ok(admin, "/records/" + normal.recordId(), secret.friendlyId(), "SECRET-PAGE");
    }

    /** GAS-P3-001/002/003 over HTTP: the session page never names a hidden Record or counts hidden Files. */
    @Test
    void gasP3_001_002_003_sessionPageDisclosesNothingHidden() {
        org.genealogyarchive.records.CaptureSession session = rx.session("Audit session");
        ArchivalFile visible = rx.file();
        rx.add(session, java.util.List.of(visible));
        rx.start(session, visible);
        rx.records().grouping().apply(rx.editor, session.captureSessionId());
        UUID record = rx.row(visible.fileId()).appliedRecordId();
        ArchivalRecord rec = rx.records().records().get(rx.admin, record);
        rx.records().records().correct(rx.admin, record, rec.version(), new RecordService.RecordInput(null, null, null,
                null, null, null, null, null, Restriction.RESTRICTED), null);

        for (String user : new String[]{"editor1", "viewer1"}) {
            String page = signedIn(user).get("/sessions/" + session.captureSessionId()).body();
            assertThat(page).contains(visible.friendlyId(), "Grouped")
                    .doesNotContain(record.toString(), rec.friendlyId());
            // GAS-P3-003: the run's Record is hidden now, but its visible File still counts.
            assertThat(page).contains("Grouping runs");
        }
        String adminPage = signedIn("admin1").get("/sessions/" + session.captureSessionId()).body();
        assertThat(adminPage).contains(record.toString());

        // GAS-P3-002: a batch of only restricted Files answers like an empty batch.
        UUID hiddenBatch = rx.fx.newBatch(rx.editor);
        rx.restrictedBatchFile(rx.editor, hiddenBatch);
        rx.restrictedBatchFile(rx.editor, hiddenBatch);
        UUID emptyBatch = rx.fx.newBatch(rx.editor);
        WebSession editor = signedIn("editor1");
        String path = "/sessions/" + session.captureSessionId();
        assertThat(editor.submit(path, path + "/files", Map.of("batchId", hiddenBatch.toString())).statusCode()).isEqualTo(302);
        String afterHidden = editor.get(path).body();
        assertThat(editor.submit(path, path + "/files", Map.of("batchId", emptyBatch.toString())).statusCode()).isEqualTo(302);
        String afterEmpty = editor.get(path).body();
        assertThat(afterHidden).contains("0 added; 0 already in a capture session; 0 not available.");
        assertThat(afterEmpty).contains("0 added; 0 already in a capture session; 0 not available.");
    }

    /**
     * GAS-P3-003 and GAS-P3-010 over HTTP: a run whose only Record became restricted is not listed, and another
     * user's batch metadata (UUID, date, uploader, note) never appears in the session page.
     */
    @Test
    void gasP3_003_010_sessionPageHidesRestrictedRunsAndOthersBatches() {
        org.genealogyarchive.records.CaptureSession session = rx.session("Second audit");
        ArchivalFile f = rx.file();
        rx.add(session, java.util.List.of(f));
        rx.start(session, f);
        rx.records().grouping().apply(rx.editor, session.captureSessionId());
        UUID record = rx.row(f.fileId()).appliedRecordId();
        ArchivalRecord rec = rx.records().records().get(rx.admin, record);
        rx.records().records().correct(rx.admin, record, rec.version(), new RecordService.RecordInput(null, null, null,
                null, null, null, null, null, Restriction.RESTRICTED), null);

        var other = rx.otherEditor();
        UUID theirs = rx.fx.preservation().ingestion().startBatch(other, "private note 51c9 (synthetic)").batchId();
        UUID mine = rx.fx.newBatch(rx.editor);
        String path = "/sessions/" + session.captureSessionId();

        String editorPage = signedIn("editor1").get(path).body();
        assertThat(editorPage).contains(mine.toString())
                .doesNotContain(theirs.toString(), "private note 51c9", "editor2")
                .contains("No grouping runs yet.");
        String viewerPage = signedIn("viewer1").get(path).body();
        assertThat(viewerPage).doesNotContain(theirs.toString(), "private note 51c9", "editor2")
                .contains("No grouping runs yet.");
        String adminPage = signedIn("admin1").get(path).body();
        assertThat(adminPage).contains(theirs.toString(), "private note 51c9", "editor2").doesNotContain("No grouping runs yet.");
    }
}
