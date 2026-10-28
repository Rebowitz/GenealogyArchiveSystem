package org.genealogyarchive.migration;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.api.FlywayException;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.data.SchemaCatalog;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UP-005 foundation: a failing migration leaves the database in its prior known state; a database
 * upgraded by a newer build is detected and not migrated by an older one. Test-only migrations live
 * under src/test/resources/db/test-migrations.
 */
class MigrationFailureIT {

    private static final String FAILING = "classpath:db/test-migrations/failing";
    private static final String FUTURE = "classpath:db/test-migrations/future";

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
    void failingTransactionalDdlLeavesNoPartialSchemaStructure() {
        new SchemaMigrator(dataSource).migrate();
        List<String> before = new SchemaCatalog(dataSource).snapshot(DataSources.APPLICATION_SCHEMA);

        SchemaMigrator withFailing = new SchemaMigrator(dataSource, SchemaMigrator.DEFAULT_LOCATION, FAILING);
        assertThatThrownBy(withFailing::migrate).isInstanceOf(FlywayException.class);

        assertThat(new SchemaCatalog(dataSource).snapshot(DataSources.APPLICATION_SCHEMA))
                .as("no partial structure from the failed migration survives")
                .isEqualTo(before)
                .noneMatch(line -> line.contains("must_not_survive"));
        assertThat(new SchemaMigrator(dataSource).status().state())
                .as("the production build still sees its own current schema")
                .isEqualTo(SchemaStatus.State.CURRENT);
        assertThat(withFailing.status().state())
                .as("the build containing the failed migration reports it as not yet applied")
                .isEqualTo(SchemaStatus.State.BEHIND);
    }

    @Test
    void olderBuildRefusesDatabaseUpgradedByNewerBuild() {
        new SchemaMigrator(dataSource, SchemaMigrator.DEFAULT_LOCATION, FUTURE).migrate();

        SchemaMigrator olderBuild = new SchemaMigrator(dataSource);
        SchemaStatus status = olderBuild.status();

        assertThat(status.state()).isEqualTo(SchemaStatus.State.AHEAD);
        assertThat(status.messages()).anyMatch(m -> m.contains("newer version"));
        assertThatThrownBy(olderBuild::migrate).isInstanceOf(SchemaMigrator.MigrationRefusedException.class);
    }
}
