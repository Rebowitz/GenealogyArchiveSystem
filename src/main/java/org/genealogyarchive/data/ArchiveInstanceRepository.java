package org.genealogyarchive.data;

import org.genealogyarchive.archive.ArchiveInstance;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The single archive_instance row (V0002). */
public final class ArchiveInstanceRepository {

    private final JdbcClient jdbc;

    public ArchiveInstanceRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** All rows; the schema permits at most one. */
    public List<ArchiveInstance> findAll() {
        return jdbc.sql("SELECT archive_id, archive_name, created_at, row_version FROM gas.archive_instance")
                .query((rs, n) -> new ArchiveInstance(Rows.uuid(rs, "archive_id"), rs.getString("archive_name"),
                        Rows.instant(rs, "created_at"), rs.getLong("row_version")))
                .list();
    }

    public Optional<ArchiveInstance> find() {
        List<ArchiveInstance> all = findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /** Locks the row (or the absence of rows is observed under the table lock) for initialization. */
    public void lockForInitialization() {
        jdbc.sql("LOCK TABLE gas.archive_instance IN SHARE ROW EXCLUSIVE MODE").update();
    }

    public void insert(UUID archiveId, String archiveName) {
        jdbc.sql("INSERT INTO gas.archive_instance (archive_id, archive_name) VALUES (:id, :name)")
                .param("id", archiveId).param("name", archiveName).update();
    }

    /** @return true if the row had {@code expectedVersion} and was updated */
    public boolean rename(long expectedVersion, String newName) {
        return jdbc.sql("""
                UPDATE gas.archive_instance
                   SET archive_name = :name, row_version = row_version + 1
                 WHERE row_version = :expected
                """).param("name", newName).param("expected", expectedVersion).update() == 1;
    }
}
