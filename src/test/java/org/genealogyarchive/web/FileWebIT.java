package org.genealogyarchive.web;

import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.config.InstallationConfig;
import org.genealogyarchive.config.InstallationConfig.FileSettings;
import org.genealogyarchive.preservation.OperationState;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.PreservationFixture;
import org.genealogyarchive.testsupport.TestDatabase;
import org.genealogyarchive.testsupport.WebSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser ingestion end to end through the web process (P2-A, B5, requirement 6), downloads and
 * restricted-file parity over HTTP (P2-H, B2). The worker side is driven directly by the test.
 */
class FileWebIT {

    private static final Pattern OP = Pattern.compile("\"operationId\":\"([0-9a-f-]{36})\"");
    private static final Pattern FILE = Pattern.compile("\"fileId\":\"([0-9a-f-]{36})\"");

    @TempDir
    Path temp;
    PreservationFixture fx;
    ConfigurableApplicationContext app;

    @BeforeEach
    void setUp() throws IOException {
        fx = PreservationFixture.create(temp);
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.close();
        }
        fx.close();
    }

    private void start(long maxUploadBytes) {
        InstallationConfig base = TestDatabase.installationConfig(fx.db.settings());
        InstallationConfig config = new InstallationConfig(base.sourceFile(), base.database(), base.server(), base.logging(),
                Map.of(fx.volumeId, fx.volumeRoot),
                new FileSettings(Optional.of(fx.staging), Optional.of(fx.cache), maxUploadBytes, Duration.ofMinutes(15)));
        app = ProcessLauncher.startWeb(config, Map.of());
    }

    private WebSession signedIn(String user) {
        WebSession s = new WebSession(app);
        assertThat(WebSession.location(s.signIn(user, ArchiveFixtures.PASSWORD))).doesNotContain("error");
        return s;
    }

    private static String batch(WebSession s) {
        HttpResponse<String> r = s.submit("/files/upload", "/files/batches", Map.of("note", "box 3"));
        assertThat(r.statusCode()).isEqualTo(302);
        String loc = WebSession.location(r);
        Matcher m = Pattern.compile("/files/batches/([0-9a-f-]{36})").matcher(loc);
        assertThat(m.find()).as(loc).isTrue();
        return m.group(1);
    }

    private static HttpResponse<String> upload(WebSession s, String batch, String key, String name, byte[] body,
                                               Map<String, String> extra, boolean chunked) {
        Map<String, String> h = new java.util.LinkedHashMap<>(Map.of(
                "X-File-Name", java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"),
                "X-File-Last-Modified", "1577934245000"));
        h.putAll(extra);
        HttpRequest.BodyPublisher p = chunked
                ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))
                : HttpRequest.BodyPublishers.ofByteArray(body);
        return s.put("/files/upload", "/files/batches/" + batch + "/items/" + key, h, p);
    }

    /** Each rendered page carries a fresh masked CSRF token; everything else must be identical. */
    private static String withoutCsrf(String html) {
        return html.replaceAll("name=\"_csrf\"\\s+value=\"[^\"]*\"", "name=\"_csrf\"")
                .replaceAll("value=\"[^\"]*\"\\s+name=\"_csrf\"", "name=\"_csrf\"");
    }

    private static String group(Pattern p, String body) {
        Matcher m = p.matcher(body);
        assertThat(m.find()).as(body).isTrue();
        return m.group(1);
    }

    private static byte[] payload(int size) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) (i * 7 + (i >> 11));
        }
        return b;
    }

    @Test
    void p2a_aLargeBrowserUploadIsStreamedStagedAndOnlyThenReportedArchived() {
        start(FileSettings.DEFAULT_MAX_UPLOAD_BYTES);
        WebSession editor = signedIn("editor1");
        String batch = batch(editor);
        byte[] body = payload(12 * 1024 * 1024 + 17); // beyond common framework defaults (1-10 MB)

        HttpResponse<String> put = upload(editor, batch, "k1", "Pfarrbuch 1803 Seite 12.tif", body, Map.of(), false);
        assertThat(put.statusCode()).as(put.body()).isEqualTo(200);
        assertThat(put.body()).contains("\"archived\":false").doesNotContain("Safely archived");
        UUID op = UUID.fromString(group(OP, put.body()));

        fx.process(op); // the worker
        HttpResponse<String> status = editor.get("/files/operations/" + op + "/status");
        assertThat(status.body()).contains("\"archived\":true", "Safely archived");
        String fileId = group(FILE, status.body());
        assertThat(fx.file(UUID.fromString(fileId)).originalFilename()).isEqualTo("Pfarrbuch 1803 Seite 12.tif");

        HttpResponse<byte[]> download = editor.getBytes("/files/" + fileId + "/download");
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(body);
        assertThat(download.headers().firstValue("Content-Disposition").orElse("")).startsWith("attachment");
        assertThat(download.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");
        assertThat(download.headers().firstValue("Content-Security-Policy").orElse("")).contains("sandbox");

        HttpResponse<String> page = editor.get("/files/" + fileId);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Pfarrbuch 1803 Seite 12.tif");

        assertThat(put.body()).contains("\"fresh\":true");
        HttpResponse<String> again = upload(editor, batch, "k1", "Pfarrbuch 1803 Seite 12.tif", body, Map.of(), false);
        assertThat(group(OP, again.body())).as("a resent item is the same operation").isEqualTo(op.toString());
        assertThat(again.body()).contains("\"fresh\":false");
    }

    @Test
    void b5_theConfiguredLimitIsEnforcedForDeclaredAndStreamedBodies() {
        start(1024 * 1024);
        WebSession editor = signedIn("editor1");
        String batch = batch(editor);
        byte[] body = payload(1536 * 1024);

        HttpResponse<String> declared = upload(editor, batch, "d", "big.tif", body, Map.of(), false);
        assertThat(declared.statusCode()).isEqualTo(413);
        assertThat(declared.body()).contains("maxUploadBytes");

        HttpResponse<String> streamed = upload(editor, batch, "s", "big.tif", body, Map.of(), true);
        assertThat(streamed.statusCode()).isEqualTo(413);
        assertThat(fx.preservation().context().operations().findByBatchItem(UUID.fromString(batch), "s").orElseThrow().state())
                .isEqualTo(OperationState.ABANDONED);
    }

    @Test
    void uploadsNeedTheCsrfTokenAndAnUploadPermission() {
        start(FileSettings.DEFAULT_MAX_UPLOAD_BYTES);
        WebSession editor = signedIn("editor1");
        String batch = batch(editor);
        HttpResponse<String> noToken = editor.putRaw("/files/batches/" + batch + "/items/x",
                Map.of("X-File-Name", "a.tif"), HttpRequest.BodyPublishers.ofString("a"));
        assertThat(noToken.statusCode()).isEqualTo(403);
        HttpResponse<String> forged = editor.putRaw("/files/batches/" + batch + "/items/y",
                Map.of("X-CSRF-TOKEN", "forged", "X-File-Name", "a.tif"), HttpRequest.BodyPublishers.ofString("a"));
        assertThat(forged.statusCode()).isEqualTo(403);
        assertThat(fx.preservation().context().operations().byBatch(UUID.fromString(batch))).isEmpty();

        WebSession viewer = signedIn("viewer1");
        assertThat(viewer.get("/files/upload").statusCode()).isEqualTo(403);
    }

    @Test
    void b2_restrictedFilesLookExactlyLikeMissingOnesToNonAdministrators() {
        start(FileSettings.DEFAULT_MAX_UPLOAD_BYTES);
        WebSession admin = signedIn("admin1");
        String batch = batch(admin);
        HttpResponse<String> put = upload(admin, batch, "r", "living.jpg", payload(1000),
                Map.of("X-Restriction", "RESTRICTED"), false);
        UUID op = UUID.fromString(group(OP, put.body()));
        String fileId = fx.process(op).fileId().toString();
        assertThat(admin.get("/files/" + fileId).statusCode()).isEqualTo(200);

        for (String user : new String[]{"editor1", "viewer1"}) {
            WebSession s = signedIn(user);
            HttpResponse<String> hidden = s.get("/files/" + fileId);
            HttpResponse<String> absent = s.get("/files/" + UUID.randomUUID());
            assertThat(hidden.statusCode()).isEqualTo(404).isEqualTo(absent.statusCode());
            assertThat(withoutCsrf(hidden.body())).isEqualTo(withoutCsrf(absent.body()));
            assertThat(s.getBytes("/files/" + fileId + "/download").statusCode()).isEqualTo(404);
            assertThat(s.get("/files").body()).doesNotContain("living.jpg");
        }
    }

    @Test
    void preservationPagesAreForAdministratorsOnly() {
        start(FileSettings.DEFAULT_MAX_UPLOAD_BYTES);
        assertThat(signedIn("admin1").get("/preservation").statusCode()).isEqualTo(200);
        assertThat(signedIn("editor1").get("/preservation").statusCode()).isEqualTo(403);
        HttpResponse<String> ops = signedIn("admin1").get("/operations");
        assertThat(ops.statusCode()).isEqualTo(200);
        assertThat(ops.body()).contains("File preservation");
        // In a test the web process deliberately has write access, so the probe must say so
        // (the native service tests prove the denied case under the real service accounts).
        assertThat(ops.body()).contains("not confirmed as denied", "can write archival originals");
    }
}
