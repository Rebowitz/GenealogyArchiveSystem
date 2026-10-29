package org.genealogyarchive.data;

import org.genealogyarchive.storage.StorageVolume;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** storage_volume rows (V0005). No native paths are stored. */
public final class StorageVolumeRepository {

    private static final String COLUMNS =
            "volume_id, name, purpose, description, required, created_at, retired_at, row_version";

    private static final RowMapper<StorageVolume> VOLUME = (rs, n) -> new StorageVolume(
            Rows.uuid(rs, "volume_id"), rs.getString("name"), rs.getString("purpose"), rs.getString("description"),
            rs.getBoolean("required"), Rows.instant(rs, "created_at"), Rows.instant(rs, "retired_at"),
            rs.getLong("row_version"));

    private final JdbcClient jdbc;

    public StorageVolumeRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public void insert(StorageVolume v) {
        jdbc.sql("""
                INSERT INTO gas.storage_volume (volume_id, name, purpose, description, required, created_at)
                VALUES (:id, :name, :purpose, :description, :required, :created)
                """)
                .param("id", v.volumeId()).param("name", v.name()).param("purpose", v.purpose())
                .param("description", v.description()).param("required", v.required())
                .param("created", Rows.param(v.createdAt()))
                .update();
    }

    public Optional<StorageVolume> findById(UUID volumeId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.storage_volume WHERE volume_id = :id")
                .param("id", volumeId).query(VOLUME).optional();
    }

    public List<StorageVolume> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.storage_volume ORDER BY retired_at NULLS FIRST, lower(name)")
                .query(VOLUME).list();
    }

    public boolean activeNameTaken(String name, UUID exceptVolumeId) {
        return jdbc.sql("""
                SELECT count(*) FROM gas.storage_volume
                 WHERE lower(name) = lower(:name) AND retired_at IS NULL
                   AND (CAST(:except AS uuid) IS NULL OR volume_id <> CAST(:except AS uuid))
                """).param("name", name).param("except", exceptVolumeId == null ? null : exceptVolumeId.toString())
                .query(Long.class).single() > 0;
    }

    /** @return true if the row had {@code expectedVersion} and was updated */
    public boolean update(UUID volumeId, long expectedVersion, String name, String purpose, String description,
                          boolean required, Instant retiredAt) {
        return jdbc.sql("""
                UPDATE gas.storage_volume
                   SET name = :name, purpose = :purpose, description = :description, required = :required,
                       retired_at = :retired, row_version = row_version + 1
                 WHERE volume_id = :id AND row_version = :expected
                """)
                .param("name", name).param("purpose", purpose).param("description", description)
                .param("required", required).param("retired", Rows.param(retiredAt))
                .param("id", volumeId).param("expected", expectedVersion)
                .update() == 1;
    }
}
