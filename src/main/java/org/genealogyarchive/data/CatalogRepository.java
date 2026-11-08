package org.genealogyarchive.data;

import org.genealogyarchive.identity.FriendlyId;
import org.genealogyarchive.records.Repository;
import org.genealogyarchive.records.Source;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** repository and source (V0021). Rows are never deleted. */
public final class CatalogRepository {

    public static final String REPOSITORY_TYPE = "repository";
    public static final String REPOSITORY_PREFIX = "RP";
    public static final String SOURCE_TYPE = "source";
    public static final String SOURCE_PREFIX = "S";

    private static final RowMapper<Repository> REPOSITORY = (rs, n) -> new Repository(Rows.uuid(rs, "repository_id"),
            new FriendlyId(REPOSITORY_PREFIX, rs.getLong("friendly_number"), 4).value(), rs.getString("name"),
            rs.getString("description"), rs.getString("location_text"), Rows.instant(rs, "created_at"),
            rs.getLong("row_version"));

    private static final RowMapper<Source> SOURCE = (rs, n) -> new Source(Rows.uuid(rs, "source_id"),
            new FriendlyId(SOURCE_PREFIX, rs.getLong("friendly_number"), 5).value(), rs.getString("title"),
            Rows.uuid(rs, "repository_id"), rs.getString("source_type"), rs.getString("volume_designation"),
            rs.getString("creator_text"), rs.getString("access_provider_text"), rs.getString("jurisdiction_text"),
            rs.getString("notes"), Rows.instant(rs, "created_at"), rs.getLong("row_version"));

    private static final String REPOSITORY_COLUMNS =
            "repository_id, friendly_number, name, description, location_text, created_at, row_version";
    private static final String SOURCE_COLUMNS = """
            source_id, friendly_number, title, repository_id, source_type, volume_designation, creator_text,
            access_provider_text, jurisdiction_text, notes, created_at, row_version""";

    private final JdbcClient jdbc;

    public CatalogRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    // ---- repositories ------------------------------------------------------------------------------

    public void insertRepository(UUID id, long number, String name, String description, String location, Instant at,
                                 UUID by) {
        jdbc.sql("""
                INSERT INTO gas.repository (repository_id, friendly_number, name, description, location_text, created_at, created_by)
                VALUES (:id, :n, :name, :d, :l, :at, :by)
                """).param("id", id).param("n", number).param("name", name).param("d", description).param("l", location)
                .param("at", Rows.param(at)).param("by", by).update();
    }

    public Optional<Repository> findRepository(UUID id) {
        return jdbc.sql("SELECT " + REPOSITORY_COLUMNS + " FROM gas.repository WHERE repository_id = :id").param("id", id)
                .query(REPOSITORY).optional();
    }

    public Optional<Repository> lockRepository(UUID id) {
        return jdbc.sql("SELECT " + REPOSITORY_COLUMNS + " FROM gas.repository WHERE repository_id = :id FOR UPDATE")
                .param("id", id).query(REPOSITORY).optional();
    }

    public Optional<Repository> findRepositoryByNumber(long number) {
        return jdbc.sql("SELECT " + REPOSITORY_COLUMNS + " FROM gas.repository WHERE friendly_number = :n").param("n", number)
                .query(REPOSITORY).optional();
    }

    public List<Repository> listRepositories(int limit) {
        return jdbc.sql("SELECT " + REPOSITORY_COLUMNS + " FROM gas.repository ORDER BY lower(name), friendly_number LIMIT :n")
                .param("n", limit).query(REPOSITORY).list();
    }

    public boolean updateRepository(UUID id, long expectedVersion, String name, String description, String location) {
        return jdbc.sql("""
                UPDATE gas.repository SET name = :name, description = :d, location_text = :l, row_version = row_version + 1
                 WHERE repository_id = :id AND row_version = :v
                """).param("name", name).param("d", description).param("l", location).param("id", id)
                .param("v", expectedVersion).update() == 1;
    }

    // ---- sources -----------------------------------------------------------------------------------

    /** Values of a Source's correctable fields. */
    public record SourceFields(String title, UUID repositoryId, String sourceType, String volumeDesignation,
                               String creatorText, String accessProviderText, String jurisdictionText, String notes) {
    }

    public void insertSource(UUID id, long number, SourceFields f, Instant at, UUID by) {
        jdbc.sql("""
                INSERT INTO gas.source (source_id, friendly_number, title, repository_id, source_type, volume_designation,
                    creator_text, access_provider_text, jurisdiction_text, notes, created_at, created_by)
                VALUES (:id, :n, :title, :repo, :type, :vol, :creator, :access, :juris, :notes, :at, :by)
                """).param("id", id).param("n", number).param("title", f.title()).param("repo", f.repositoryId())
                .param("type", f.sourceType()).param("vol", f.volumeDesignation()).param("creator", f.creatorText())
                .param("access", f.accessProviderText()).param("juris", f.jurisdictionText()).param("notes", f.notes())
                .param("at", Rows.param(at)).param("by", by).update();
    }

    public Optional<Source> findSource(UUID id) {
        return jdbc.sql("SELECT " + SOURCE_COLUMNS + " FROM gas.source WHERE source_id = :id").param("id", id)
                .query(SOURCE).optional();
    }

    public Optional<Source> lockSource(UUID id) {
        return jdbc.sql("SELECT " + SOURCE_COLUMNS + " FROM gas.source WHERE source_id = :id FOR UPDATE").param("id", id)
                .query(SOURCE).optional();
    }

    public Optional<Source> findSourceByNumber(long number) {
        return jdbc.sql("SELECT " + SOURCE_COLUMNS + " FROM gas.source WHERE friendly_number = :n").param("n", number)
                .query(SOURCE).optional();
    }

    public List<Source> listSources(UUID repositoryId, int limit) {
        return jdbc.sql("SELECT " + SOURCE_COLUMNS + """
                 FROM gas.source WHERE (CAST(:repo AS uuid) IS NULL OR repository_id = :repo)
                 ORDER BY lower(title), friendly_number LIMIT :n
                """).param("repo", repositoryId).param("n", limit).query(SOURCE).list();
    }

    public boolean updateSource(UUID id, long expectedVersion, SourceFields f) {
        return jdbc.sql("""
                UPDATE gas.source SET title = :title, repository_id = :repo, source_type = :type, volume_designation = :vol,
                       creator_text = :creator, access_provider_text = :access, jurisdiction_text = :juris, notes = :notes,
                       row_version = row_version + 1
                 WHERE source_id = :id AND row_version = :v
                """).param("title", f.title()).param("repo", f.repositoryId()).param("type", f.sourceType())
                .param("vol", f.volumeDesignation()).param("creator", f.creatorText()).param("access", f.accessProviderText())
                .param("juris", f.jurisdictionText()).param("notes", f.notes()).param("id", id).param("v", expectedVersion)
                .update() == 1;
    }
}
