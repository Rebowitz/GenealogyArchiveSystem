package org.genealogyarchive.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.Driver;
import org.postgresql.jdbc.SslMode;
import org.postgresql.util.PSQLException;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * GAS-P0-R01: application validation and PostgreSQL JDBC driver interpretation must not disagree.
 * <p>
 * Every expectation about the driver is checked against the driver itself
 * ({@link Driver#parseURL(String, Properties)} and {@link SslMode#of(Properties)} of the PgJDBC version
 * on the classpath), never against the application's own parser.
 */
class JdbcUrlsDriverAgreementTest {

    private static final String BASE = "jdbc:postgresql://db.example.org:5432/gas";

    private static Properties driverView(String url) {
        Properties parsed = Driver.parseURL(url, new Properties());
        assertThat(parsed).as("PgJDBC parses the URL").isNotNull();
        return parsed;
    }

    private static SslMode driverSslMode(String url) throws PSQLException {
        return SslMode.of(driverView(url));
    }

    /** Agreement property: every approved option has exactly the value the driver will use. */
    private static void assertDriverAgreesWithApproval(String url) throws PSQLException {
        Map<String, String> approved = JdbcUrls.approvedOptions(url);
        Properties driver = driverView(url);
        for (Map.Entry<String, String> option : approved.entrySet()) {
            assertThat(driver.getProperty(option.getKey()))
                    .as("driver value of approved option %s", option.getKey())
                    .isEqualTo(option.getValue());
        }
        String approvedMode = approved.getOrDefault(JdbcUrls.SSL_MODE, "prefer"); // PgJDBC default
        assertThat(driverSslMode(url)).isEqualTo(SslMode.valueOf(approvedMode.toUpperCase(java.util.Locale.ROOT).replace('-', '_')));
    }

    // ---- canonical security-sensitive configuration --------------------------------------------

    @Test
    void canonicalVerifyFullIsAcceptedAndTheDriverAppliesVerifyFull() throws PSQLException {
        String url = BASE + "?sslmode=verify-full";

        assertThat(JdbcUrls.approvedOptions(url)).containsExactly(entry("sslmode", "verify-full"));
        assertThat(driverSslMode(url)).isEqualTo(SslMode.VERIFY_FULL);
        InstallationConfig.DatabaseSettings settings =
                new InstallationConfig.DatabaseSettings(url, "gas", Secret.of("pw"), 2);
        assertThat(settings.jdbcUrl()).as("approved URL is passed to the driver unchanged").isEqualTo(url);
        assertDriverAgreesWithApproval(url);
    }

    @ParameterizedTest(name = "sslmode={0}")
    @ValueSource(strings = {"disable", "allow", "prefer", "require", "verify-ca", "verify-full"})
    void everyCanonicalSslModeIsAcceptedAndAppliedByTheDriver(String mode) throws PSQLException {
        assertDriverAgreesWithApproval(BASE + "?sslmode=" + mode);
    }

    @Test
    void noOptionsMeansTheDriverDefaultWhichTheValidatorDoesNotClaimOtherwise() throws PSQLException {
        assertThat(JdbcUrls.approvedOptions(BASE)).isEmpty();
        assertThat(driverSslMode(BASE)).isEqualTo(SslMode.PREFER);
    }

    // ---- reviewer-reproduced divergences and related variants: rejected ----------------------

    /**
     * Each URL is one the previous validator accepted (or could misread). The driver column records
     * what PgJDBC actually does with it, proving why acceptance would have been unsafe.
     */
    @ParameterizedTest(name = "{0} -> driver {1}")
    @CsvSource({
            "?SSLMODE=verify-full,                       PREFER",
            "?ssl%6dode=verify-full,                     PREFER",
            "?SslMode=verify-full,                       PREFER",
            "?%73slmode=verify-full,                     PREFER",
            "?sslmode=verify-full&sslmode=disable,       DISABLE",
            "?sslmode=disable&sslmode=verify-full,       VERIFY_FULL",
            "?sslmode=verify-full&SSLMODE=disable,       VERIFY_FULL",
            "?SSLMODE=disable&sslmode=verify-full,       VERIFY_FULL",
            "?sslmode=verify-full&ssl%6dode=disable,     VERIFY_FULL"
    })
    void noncanonicalOrDuplicateSslModeIsRejected(String query, SslMode driverEffective) throws PSQLException {
        String url = BASE + query.strip();

        assertThat(driverSslMode(url)).as("what PgJDBC would actually apply").isEqualTo(driverEffective);
        assertThatThrownBy(() -> JdbcUrls.approvedOptions(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("SSLMODE")
                .hasMessageNotContaining("%6d")
                .hasMessageNotContaining("%73");
        assertThatThrownBy(() -> new InstallationConfig.DatabaseSettings(url, "gas", Secret.of("pw"), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?sslmode=VERIFY-FULL", "?sslmode=Verify-Full", "?sslmode=verify%2Dfull",
            "?sslmode=verify-full%20", "?sslmode=", "?sslmode=verify-full=x", "?sslmode=verify-full?x=1"})
    void noncanonicalSslModeValuesAreRejectedRatherThanNormalised(String query) {
        assertThatThrownBy(() -> JdbcUrls.approvedOptions(BASE + query)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?connectTimeout=10&connectTimeout=20", "?sslrootcert=/a&sslrootcert=/b",
            "?tcpKeepAlive=true&tcpKeepAlive=false"})
    void duplicatedNonSecurityOptionsAreAlsoRejected(String query) {
        assertThatThrownBy(() -> JdbcUrls.approvedOptions(BASE + query)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void caseVariantOfAnOrdinaryOptionIsRejectedBecauseTheDriverWouldIgnoreIt() {
        String url = BASE + "?connecttimeout=10";
        assertThat(driverView(url).getProperty("connectTimeout")).as("PgJDBC ignores the case variant").isNull();
        assertThatThrownBy(() -> JdbcUrls.approvedOptions(url)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- GAS-P0-001 credential-bearing parameters remain prohibited ---------------------------

    @ParameterizedTest
    @ValueSource(strings = {"?password=URL_SECRET", "?PASSWORD=URL_SECRET", "?%70assword=URL_SECRET",
            "?user=URL_SECRET", "?sslpassword=URL_SECRET", "?sslkey=URL_SECRET", "?sslcert=URL_SECRET",
            "?options=URL_SECRET", "?ssl=true", "?sslfactory=URL_SECRET", "?service=URL_SECRET",
            "?sslmode=verify-full&password=URL_SECRET", "?password%253DURL_SECRET=x"})
    void credentialAndExtensionParametersRemainRejectedWithoutEcho(String query) {
        assertThatThrownBy(() -> new InstallationConfig.DatabaseSettings(BASE + query, "gas", Secret.of("ENV_SECRET"), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("URL_SECRET")
                .hasMessageNotContaining("ENV_SECRET");
    }

    @Test
    void literalPasswordParameterIsHonouredByTheDriverWhichIsWhyItIsRejected() {
        assertThat(driverView(BASE + "?password=URL_SECRET").getProperty("password")).isEqualTo("URL_SECRET");
        assertThatThrownBy(() -> JdbcUrls.approvedOptions(BASE + "?password=URL_SECRET"))
                .hasMessageNotContaining("URL_SECRET");
    }

    // ---- ordinary permitted canonical options --------------------------------------------------

    @Test
    void ordinaryCanonicalOptionsAreAcceptedAndTheDriverUsesExactlyTheApprovedValues() throws PSQLException {
        String url = BASE + "?sslmode=verify-full&sslrootcert=%2Fetc%2Fgenealogy-archive%2Froot.crt"
                + "&connectTimeout=10&socketTimeout=30&tcpKeepAlive=true&targetServerType=primary"
                + "&loadBalanceHosts=false&hostRecheckSeconds=10";

        Map<String, String> approved = JdbcUrls.approvedOptions(url);

        assertThat(approved.keySet()).isEqualTo(Set.copyOf(JdbcUrls.SUPPORTED_OPTIONS));
        assertThat(approved).containsEntry("sslrootcert", "/etc/genealogy-archive/root.crt");
        assertDriverAgreesWithApproval(url);
        assertThat(driverSslMode(url)).isEqualTo(SslMode.VERIFY_FULL);
    }

    @Test
    void installationLoaderRejectsDivergentSpellingWithoutEchoingIt() {
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("url", BASE + "?SSLMODE=verify-full");
        db.put("username", "gas");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("database", db);

        assertThatThrownBy(() -> InstallationConfigLoader.parse(Path.of("installation.yml"), root, Map.of("GAS_DB_PASSWORD", "pw")))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("database.url")
                .hasMessageContaining("not in canonical spelling")
                .hasMessageNotContaining("SSLMODE");
    }
}
