package org.genealogyarchive.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** GAS-P0-001 / SEC-010: constructor and diagnostics must not bypass the loader boundary. */
class JdbcUrlsTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "?password=URL_SECRET", "?%70assword=URL_SECRET", "?PASSWORD=URL_SECRET",
            "?sslpassword=URL_SECRET", "?user=URL_SECRET", "?options=URL_SECRET",
            "?password%253DURL_SECRET=x", "?sslmode=%ZZURL_SECRET"
    })
    void rejectsSecretBearingOrUnsupportedQueryWithoutEcho(String suffix) {
        assertThatThrownBy(() -> new InstallationConfig.DatabaseSettings(
                "jdbc:postgresql://localhost/gas" + suffix, "gas", Secret.of("env-secret"), 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("URL_SECRET")
                .hasMessageNotContaining("env-secret");
    }

    @Test
    void rejectsUserInfoWithoutEcho() {
        assertThatThrownBy(() -> JdbcUrls.requireSafe("jdbc:postgresql://gas:URL_SECRET@localhost/gas"))
                .hasMessageNotContaining("URL_SECRET");
    }

    @Test
    void permitsDocumentedConnectionOptionsButOmitsValuesFromDiagnostics() {
        String url = "jdbc:postgresql://localhost/gas?sslmode=verify-full&connectTimeout=10";
        InstallationConfig.DatabaseSettings db = new InstallationConfig.DatabaseSettings(url,"gas",Secret.of("env-secret"),2);
        assertThat(db.jdbcUrl()).isEqualTo(url);
        assertThat(db.toString()).contains("options omitted").doesNotContain("verify-full","env-secret");
    }
}
