package org.genealogyarchive.migration;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.data.PostgresServer;
import org.genealogyarchive.data.SchemaCatalog;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PHASE-0 tests: "migrations apply successfully to a clean database"; "repeated migration execution
 * reaches the same expected schema state"; application runs against the supported PostgreSQL
 * version (UP-004 foundation).
 */
class MigrationIT {

    private TestDatabase db;
    private HikariDataSource dataSource;

    @BeforeEach
    void createDatabase() {
        db = TestDatabase.createFresh();
        dataSource = DataSources.create(db.settings(), "test");
    }

    @AfterEach
    void dropDatabase() {
        dataSource.close();
        db.close();
    }

    @Test
    void serverMeetsSupportedBaseline() {
        PostgresServer server = new PostgresServer(dataSource);
        server.requireSupportedVersion();
        assertThat(PostgresServer.majorVersion(server.versionNumber())).isGreaterThanOrEqualTo(16);
    }

    @Test
    void cleanDatabaseIsReportedAsNotInitialised() {
        SchemaStatus status = new SchemaMigrator(dataSource).status();
        assertThat(status.state()).isEqualTo(SchemaStatus.State.NOT_INITIALIZED);
        assertThat(status.pendingVersions()).isNotEmpty();
        assertThat(status.currentVersion()).isNull();
    }

    @Test
    void migrationsApplyToCleanDatabaseAndPassPostMigrationValidation() {
        SchemaMigrator migrator = new SchemaMigrator(dataSource);

        SchemaMigrator.MigrationReport report = migrator.migrate();

        assertThat(report.migrationsApplied()).isGreaterThanOrEqualTo(1);
        assertThat(report.fromVersion()).isNull();
        assertThat(migrator.status().state()).isEqualTo(SchemaStatus.State.CURRENT);
        assertThat(new PostMigrationValidator(dataSource, migrator).validate()).isEmpty();
    }

    @Test
    void repeatedMigrationIsANoOpWithIdenticalSchema() {
        SchemaMigrator migrator = new SchemaMigrator(dataSource);
        migrator.migrate();
        List<String> first = new SchemaCatalog(dataSource).snapshot(DataSources.APPLICATION_SCHEMA);

        SchemaMigrator.MigrationReport second = migrator.migrate();
        SchemaMigrator.MigrationReport third = new SchemaMigrator(dataSource).migrate();

        assertThat(second.migrationsApplied()).isZero();
        assertThat(third.migrationsApplied()).isZero();
        assertThat(first).isNotEmpty();
        assertThat(new SchemaCatalog(dataSource).snapshot(DataSources.APPLICATION_SCHEMA)).isEqualTo(first);
        assertThat(new PostMigrationValidator(dataSource, migrator).validate()).isEmpty();
    }

    @Test
    void independentCleanDatabasesReachTheSameSchema() {
        new SchemaMigrator(dataSource).migrate();
        List<String> here = new SchemaCatalog(dataSource).snapshot(DataSources.APPLICATION_SCHEMA);

        try (TestDatabase other = TestDatabase.createFresh();
             HikariDataSource otherDs = DataSources.create(other.settings(), "test")) {
            new SchemaMigrator(otherDs).migrate();
            assertThat(new SchemaCatalog(otherDs).snapshot(DataSources.APPLICATION_SCHEMA)).isEqualTo(here);
        }
    }

    @Test
    void applicationTablesLiveOnlyInTheApplicationSchema() throws SQLException {
        new SchemaMigrator(dataSource).migrate();
        try (Connection c = db.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    void schemaIdentityMarkerIsASingleRow() throws SQLException {
        new SchemaMigrator(dataSource).migrate();
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("INSERT INTO gas.schema_identity (application) VALUES ('genealogy-archive-system')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void phaseZeroCreatesNoHistoricalDomainTables() {
        new SchemaMigrator(dataSource).migrate();
        List<String> relations = new SchemaCatalog(dataSource).snapshot(DataSources.APPLICATION_SCHEMA).stream()
                .filter(line -> line.startsWith("relation ") && line.endsWith("kind=r"))
                .toList();
        assertThat(relations).containsExactlyInAnyOrder(
                "relation flyway_schema_history kind=r",
                "relation schema_identity kind=r");
    }
}
