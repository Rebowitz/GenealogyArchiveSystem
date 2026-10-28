package org.genealogyarchive.web;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.boot.ProcessLauncher;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.security.AppUser;
import org.genealogyarchive.testsupport.ArchiveFixtures;
import org.genealogyarchive.testsupport.Http;
import org.genealogyarchive.testsupport.TestDatabase;
import org.genealogyarchive.testsupport.WebSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.genealogyarchive.testsupport.ArchiveFixtures.PASSWORD;

/**
 * Phase 1 web sign-in and authorization over real HTTP: local authentication (SEC-009 foundation),
 * CSRF, session renewal at sign-in, role enforcement (SEC-001/003), automatic attribution (SEC-007),
 * stale-save conflict (SEC-008), and readiness requiring an initialized archive (owner decision B).
 */
@ExtendWith(OutputCaptureExtension.class)
class WebSecurityIT {

    private TestDatabase db;
    private ConfigurableApplicationContext app;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.close();
        }
        db.close();
    }

    private void startInitialized() {
        ArchiveFixtures.migrateAndInitialize(db);
        ArchiveFixtures.createUser(db, "admin", Role.ADMINISTRATOR);
        ArchiveFixtures.createUser(db, "viewer", Role.VIEWER);
        app = ProcessLauncher.startWeb(db.installationConfig(), Map.of());
    }

    private static String versionField(String html) {
        Matcher m = Pattern.compile("name=\"version\"\\s+value=\"(\\d+)\"").matcher(html);
        assertThat(m.find()).as("form carries the record version").isTrue();
        return m.group(1);
    }

    @Test
    void migratedButUninitializedInstanceIsVisiblyNotReadyUntilInitArchive() {
        try (HikariDataSource ds = DataSources.create(db.settings(), "test")) {
            new SchemaMigrator(ds).migrate();
        }
        app = ProcessLauncher.startWeb(db.installationConfig(), Map.of());
        assertThat(Http.get(app, "/actuator/health/readiness").statusCode()).isEqualTo(503);
        assertThat(Http.get(app, "/").body()).contains("Archive instance is not ready", "init-archive");

        try (HikariDataSource ds = DataSources.create(db.settings(), "test")) {
            ArchiveFixtures.services(ds).archives().initialize(org.genealogyarchive.access.Actor.commandLine(), "Now Ready");
        }
        assertThat(Http.get(app, "/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    void protectedPagesRequireSignInAndPublicHomeShowsNoArchiveIdentity() {
        startInitialized();
        WebSession anonymous = new WebSession(app);
        HttpResponse<String> archive = anonymous.get("/archive");
        assertThat(archive.statusCode()).isEqualTo(302);
        assertThat(WebSession.location(archive)).endsWith("/login");
        HttpResponse<String> home = anonymous.get("/");
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("Archive instance is ready").doesNotContain("Test Archive").doesNotContain("Archive ID");
        assertThat(anonymous.get("/actuator/health/liveness").statusCode()).as("service managers need health").isEqualTo(200);
    }

    @Test
    void signInWithLocalAccountRenewsTheSessionAndShowsTheArchive() {
        startInitialized();
        WebSession session = new WebSession(app);
        HttpResponse<String> loginPage = session.get("/login");
        String before = loginPage.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElse("");

        HttpResponse<String> signIn = session.signIn("admin", PASSWORD);

        assertThat(signIn.statusCode()).isEqualTo(302);
        assertThat(WebSession.location(signIn)).doesNotContain("error");
        String after = signIn.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElse("");
        assertThat(after).as("a new session id is issued at sign-in").isNotEmpty().isNotEqualTo(before);
        assertThat(after).containsIgnoringCase("HttpOnly");
        HttpResponse<String> home = session.get("/");
        assertThat(home.body()).contains("Test Archive", "Archive ID");
    }

    @Test
    void wrongPasswordFailsWithoutRevealingWhichPartWasWrongOrEchoingIt(CapturedOutput output) {
        startInitialized();
        WebSession session = new WebSession(app);
        String wrong = "Wr0ng-Passphrase-77aa";
        HttpResponse<String> attempt = session.signIn("admin", wrong);
        assertThat(WebSession.location(attempt)).endsWith("/login?error");
        HttpResponse<String> unknownUser = new WebSession(app).signIn("nobody", wrong);
        assertThat(WebSession.location(unknownUser)).endsWith("/login?error");
        assertThat(session.get("/login?error").body()).contains("username or password is not correct").doesNotContain(wrong);
        assertThat(session.get("/archive").statusCode()).isEqualTo(302);
        assertThat(output.getAll()).doesNotContain(wrong).doesNotContain(PASSWORD);
    }

    @Test
    void formsWithoutACsrfTokenAreRejected() {
        startInitialized();
        WebSession session = new WebSession(app);
        session.signIn("admin", PASSWORD);
        HttpResponse<String> forged = session.postRaw("/archive", Map.of("version", "1", "archiveName", "Forged"));
        assertThat(forged.statusCode()).isEqualTo(403);
        assertThat(session.get("/archive").body()).doesNotContain("Forged");
    }

    @Test
    void viewerIsRefusedAdministrativePagesAndActionsEvenWhenCallingUrlsDirectly() {
        startInitialized();
        WebSession viewer = new WebSession(app);
        viewer.signIn("viewer", PASSWORD);
        assertThat(viewer.get("/archive").statusCode()).as("viewers may read").isEqualTo(200);
        assertThat(viewer.get("/users").statusCode()).isEqualTo(403);
        assertThat(viewer.get("/operations").statusCode()).isEqualTo(403);
        HttpResponse<String> rename = viewer.submit("/archive", "/archive", Map.of("version", "1", "archiveName", "Viewer Was Here"));
        assertThat(rename.statusCode()).isEqualTo(403);
        HttpResponse<String> addUser = viewer.submit("/archive", "/users",
                Map.of("username", "intruder", "displayName", "I", "role", "ADMINISTRATOR", "password", PASSWORD));
        assertThat(addUser.statusCode()).isEqualTo(403);
        assertThat(viewer.get("/archive").body()).doesNotContain("Viewer Was Here");
    }

    @Test
    void administratorCorrectionIsAttributedAutomaticallyAndStaleFormsConflict() {
        startInitialized();
        WebSession admin = new WebSession(app);
        admin.signIn("admin", PASSWORD);
        String page = admin.get("/archive").body();
        String version = versionField(page);

        HttpResponse<String> saved = admin.submit("/archive", "/archive", Map.of("version", version, "archiveName", "Andrews Research"));
        assertThat(saved.statusCode()).isEqualTo(302);
        String after = admin.get("/archive").body();
        assertThat(after).contains("Andrews Research", "CORRECTED", "admin");

        HttpResponse<String> stale = admin.submit("/archive", "/archive", Map.of("version", version, "archiveName", "Stale Edit"));
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(stale.body()).contains("changed by someone else");
        assertThat(admin.get("/archive").body()).doesNotContain("Stale Edit");
    }

    @Test
    void disablingAnAccountEndsItsExistingSession() {
        startInitialized();
        WebSession viewer = new WebSession(app);
        viewer.signIn("viewer", PASSWORD);
        assertThat(viewer.get("/archive").statusCode()).isEqualTo(200);

        try (HikariDataSource ds = DataSources.create(db.settings(), "test")) {
            var core = ArchiveFixtures.services(ds);
            AppUser adminUser = core.users().credentials("admin").orElseThrow().user();
            AppUser target = core.users().credentials("viewer").orElseThrow().user();
            core.users().update(adminUser.asActor(), target.userId(), target.version(), target.displayName(), Role.VIEWER, false);
        }
        HttpResponse<String> next = viewer.get("/archive");
        assertThat(next.statusCode()).isEqualTo(302);
        assertThat(WebSession.location(next)).contains("/login");
        assertThat(new WebSession(app).signIn("viewer", PASSWORD).headers().firstValue("Location").orElse(""))
                .as("a disabled account cannot sign in").endsWith("/login?error");
    }

    @Test
    void administratorCanRegisterAVolumeAndCreateAUserThroughTheBrowser() {
        startInitialized();
        WebSession admin = new WebSession(app);
        admin.signIn("admin", PASSWORD);
        HttpResponse<String> registered = admin.submit("/volumes", "/volumes",
                Map.of("name", "Primary NAS", "purpose", "primary", "required", "true"));
        assertThat(registered.statusCode()).isEqualTo(302);
        String volumePath = WebSession.location(registered).replaceFirst("^https?://[^/]+", "");
        String volumePage = admin.get(volumePath).body();
        assertThat(volumePage).contains("Primary NAS", "storageRoots");
        UUID.fromString(volumePath.substring(volumePath.lastIndexOf('/') + 1));

        HttpResponse<String> created = admin.submit("/users", "/users",
                Map.of("username", "editor1", "displayName", "Editor One", "role", "EDITOR", "password", PASSWORD));
        assertThat(created.statusCode()).isEqualTo(302);
        assertThat(admin.get("/users").body()).contains("editor1", "EDITOR");
    }
}
