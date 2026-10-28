package org.genealogyarchive.migration;

import org.genealogyarchive.data.SchemaIdentityRepository;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks run after every migration (BUILD-SPEC s.56 "Run post-migration validation", UP-004).
 * Later phases add checks for their own invariants here; each check must be read-only.
 */
public final class PostMigrationValidator {

    private final SchemaMigrator migrator;
    private final SchemaIdentityRepository identity;

    public PostMigrationValidator(DataSource dataSource, SchemaMigrator migrator) {
        this.migrator = migrator;
        this.identity = new SchemaIdentityRepository(dataSource);
    }

    /** @return problems found; empty means the migrated schema is valid */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        SchemaStatus status = migrator.status();
        if (!status.isCurrent()) {
            problems.add("Schema is not current after migration: " + status.state() + " - "
                    + String.join(" ", status.messages()));
            return problems;
        }
        List<String> markers = identity.applicationMarkers();
        if (markers.size() != 1) {
            problems.add("Expected exactly one schema identity marker, found " + markers.size());
        } else if (!SchemaIdentityRepository.EXPECTED_APPLICATION.equals(markers.get(0))) {
            problems.add("Database belongs to a different application: '" + markers.get(0) + "'");
        }
        return problems;
    }
}
