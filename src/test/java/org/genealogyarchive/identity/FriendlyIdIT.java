package org.genealogyarchive.identity;

import com.zaxxer.hikari.HikariDataSource;
import org.genealogyarchive.data.DataSources;
import org.genealogyarchive.data.FriendlyIdSeriesRepository;
import org.genealogyarchive.data.Transactions;
import org.genealogyarchive.migration.PostMigrationValidator;
import org.genealogyarchive.migration.SchemaMigrator;
import org.genealogyarchive.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A-002 / BUILD-SPEC s.8 friendly-identifier infrastructure, exercised with a TEST-ONLY object type
 * (src/test/resources/db/test-migrations/friendly-id). No domain object type is created.
 */
class FriendlyIdIT {

    private static final String TEST_LOCATION = "classpath:db/test-migrations/friendly-id";

    private TestDatabase db;
    private HikariDataSource ds;
    private FriendlyIdSeriesRepository series;

    @BeforeEach
    void setUp() {
        db = TestDatabase.createFresh();
        ds = DataSources.create(db.settings(), "test");
        new SchemaMigrator(ds, SchemaMigrator.DEFAULT_LOCATION, TEST_LOCATION).migrate();
        series = new FriendlyIdSeriesRepository(ds);
    }

    @AfterEach
    void tearDown() {
        ds.close();
        db.close();
    }

    private void insertWidget(UUID id, FriendlyId friendly) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO gas.test_widget (widget_id, friendly_id) VALUES (?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, friendly.value());
            ps.executeUpdate();
        }
    }

    @Test
    void allocatesSequentialFormattedIdentifiers() {
        assertThat(series.allocate("test_widget").value()).isEqualTo("TW00001");
        assertThat(series.allocate("test_widget").value()).isEqualTo("TW00002");
    }

    @Test
    void identifiersAreNeverReusedAfterDeletionOrRollback() throws SQLException {
        FriendlyId first = series.allocate("test_widget");
        UUID firstId = GlobalIds.newId();
        insertWidget(firstId, first);
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM gas.test_widget WHERE widget_id = '" + firstId + "'");
        }
        // A rolled-back allocation leaves a gap; gaps are valid and never refilled.
        assertThatThrownBy(() -> Transactions.template(ds).executeWithoutResult(status -> {
            series.allocate("test_widget");
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        Set<String> issued = new HashSet<>();
        issued.add(first.value());
        for (int i = 0; i < 20; i++) {
            assertThat(issued.add(series.allocate("test_widget").value())).as("never reissued").isTrue();
        }
        assertThat(issued).as("the rolled-back number stays a gap").doesNotContain("TW00002");
    }

    @Test
    void friendlyIdentifierIsNotTheUnderlyingIdentity() throws SQLException {
        UUID id = GlobalIds.newId();
        FriendlyId friendly = series.allocate("test_widget");
        insertWidget(id, friendly);
        assertThat(id.toString()).doesNotContain(friendly.value());
        assertThat(GlobalIds.isVersion7(id)).isTrue();
    }

    @Test
    void unregisteredTypeFailsVisibly() {
        assertThatThrownBy(() -> series.allocate("person")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No friendly identifier series");
    }

    @Test
    void registeredSeriesCannotBeAlteredOrRemoved() {
        assertThatThrownBy(() -> {
            try (Connection c = db.connect(); Statement s = c.createStatement()) {
                s.execute("UPDATE gas.friendly_id_series SET prefix = 'XX'");
            }
        }).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> {
            try (Connection c = db.connect(); Statement s = c.createStatement()) {
                s.execute("DELETE FROM gas.friendly_id_series");
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void postMigrationValidationDetectsASequenceThatCouldReuseNumbers() throws SQLException {
        SchemaMigrator migrator = new SchemaMigrator(ds, SchemaMigrator.DEFAULT_LOCATION, TEST_LOCATION);
        assertThat(new PostMigrationValidator(ds, migrator).validate()).isEmpty();
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.execute("ALTER SEQUENCE gas.friendly_id_seq_test_widget CYCLE");
        }
        assertThat(new PostMigrationValidator(ds, migrator).validate())
                .anyMatch(p -> p.contains("friendly_id_seq_test_widget"));
    }
}
