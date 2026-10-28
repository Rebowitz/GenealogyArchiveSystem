package org.genealogyarchive.testsupport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PHASE-0: test isolation does not depend on (and cannot damage) production data. */
class TestDatabaseGuardTest {

    @Test
    void refusesToDropNonTestDatabases() {
        assertThatThrownBy(() -> TestDatabase.requireTestDatabaseName("gas")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TestDatabase.requireTestDatabaseName("postgres")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TestDatabase.requireTestDatabaseName("gas_test_x; DROP DATABASE gas"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> TestDatabase.requireTestDatabaseName("gas_test_0123456789abcdef")).doesNotThrowAnyException();
    }

    @Test
    void derivesPerTestDatabaseUrlFromAdministrativeUrl() {
        assertThat(TestDatabase.databaseUrl("jdbc:postgresql://localhost:5432/postgres", "gas_test_ab"))
                .isEqualTo("jdbc:postgresql://localhost:5432/gas_test_ab");
        assertThat(TestDatabase.databaseUrl("jdbc:postgresql://db:5433/postgres?sslmode=disable", "gas_test_ab"))
                .isEqualTo("jdbc:postgresql://db:5433/gas_test_ab?sslmode=disable");
    }
}
