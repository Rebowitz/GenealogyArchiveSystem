package org.genealogyarchive.data;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * unresolved_object rows (V0019): durable records of occupied locations whose object the archive cannot show
 * to be its own. The object is never moved or deleted on the strength of a record; a record closes only on
 * verified absence, on an Administrator's resolution, or when an attested object is moved to quarantine.
 * Never deleted; a resolution is final.
 */
public final class UnresolvedObjectRepository {

    /** What the archive knew about the location when the record was made. */
    public enum Kind {
        /** A superseded generation that was found damaged; the object there is unverified. */
        DAMAGED_COPY,
        /** A location the archive recorded the bytes leaving; something is there now. */
        OBJECT_AT_VACATED_LOCATION,
        /** An operation's claimed, uncommitted copy that does not verify as the bytes it was writing. */
        OPERATION_COPY,
        /** A quarantined object without trusted evidence of what was moved there. */
        QUARANTINED_COPY,
        /** The current generation of content being destroyed; the object there does not verify. */
        CURRENT_COPY
    }

    public enum Resolution { ABSENT, NOT_ARCHIVAL, QUARANTINED, DESTROYED }

    /** V0020: how much was recorded about the object observed. */
    public enum IdentityEvidence {
        /** A plain file measured with its times (and file key where the platform gives one). */
        RECORDED,
        /** Not a plain file: nothing to identify. */
        NONE,
        /** Recorded before V0020: never identifies an object for an attestation or a release. */
        LEGACY
    }

    /** V0020: whether the object examined at an Administrator's decision was exactly the one recorded. */
    public enum DecisionEvidence { CONFIRMED, UNCONFIRMED }

    /**
     * What identifies one physical object as far as the platform allows (V0020): size, SHA-256, creation and
     * last-modification times (to the microsecond, as PostgreSQL keeps them) and the file key where there is one
     * (null on Windows/NTFS). Compared only for exact equality. Evidence, not proof: an object given the same bytes,
     * times and file key cannot be told apart.
     */
    public record Identity(long size, String sha256, Instant created, Instant modified, String fileKey) {
        public Identity {
            created = created.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            modified = modified.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        }
    }

    /**
     * @param observedSize   size seen when the record was made - diagnostic only
     * @param observedSha256 SHA-256 seen then - diagnostic only; an Administrator may attest that it is the
     *                       archive's object, which is the only way it can authorise a move
     */
    public record Entry(UUID unresolvedId, UUID volumeId, String path, UUID contentId, Integer generation,
                        UUID operationId, Kind kind, Long observedSize, String observedSha256, String detail,
                        Instant detectedAt, UUID attestedBy, Instant attestedAt, boolean open, Resolution resolution,
                        String resolutionDetail, UUID resolvedBy, Instant resolvedAt, Instant observedCreatedAt,
                        Instant observedModifiedAt, String observedFileKey, IdentityEvidence identityEvidence,
                        DecisionEvidence decisionEvidence, Instant decisionCheckedAt) {

        public boolean attested() {
            return attestedAt != null;
        }

        /** The identity recorded at the observation, if one was (V0020); empty for NONE and LEGACY records. */
        public Optional<Identity> recordedIdentity() {
            return identityEvidence == IdentityEvidence.RECORDED
                    ? Optional.of(new Identity(observedSize, observedSha256, observedCreatedAt, observedModifiedAt, observedFileKey))
                    : Optional.empty();
        }

        /** Whether the Administrator's decision was made about exactly the recorded object (V0020). */
        public boolean decisionConfirmed() {
            return decisionEvidence == DecisionEvidence.CONFIRMED && recordedIdentity().isPresent();
        }
    }

    /**
     * A record to make.
     *
     * @param observed the object's identity as observed now; null if there was no plain file to identify
     */
    public record NewEntry(UUID volumeId, String path, UUID contentId, Integer generation, UUID operationId, Kind kind,
                           Identity observed, String detail) {
    }

    private static final String COLUMNS = """
            unresolved_id, volume_id, path, content_id, generation, operation_id, kind, observed_size, observed_sha256,
            detail, detected_at, attested_by, attested_at, status, resolution, resolution_detail, resolved_by, resolved_at,
            observed_created_at, observed_modified_at, observed_file_key, identity_evidence, decision_evidence,
            decision_checked_at""";

    private static final RowMapper<Entry> ENTRY = (rs, n) -> {
        String resolution = rs.getString("resolution");
        return new Entry(Rows.uuid(rs, "unresolved_id"), Rows.uuid(rs, "volume_id"), rs.getString("path"),
                Rows.uuid(rs, "content_id"), (Integer) rs.getObject("generation"), Rows.uuid(rs, "operation_id"),
                Kind.valueOf(rs.getString("kind")), rs.getObject("observed_size", Long.class), rs.getString("observed_sha256"),
                rs.getString("detail"), Rows.instant(rs, "detected_at"), Rows.uuid(rs, "attested_by"),
                Rows.instant(rs, "attested_at"), "OPEN".equals(rs.getString("status")),
                resolution == null ? null : Resolution.valueOf(resolution), rs.getString("resolution_detail"),
                Rows.uuid(rs, "resolved_by"), Rows.instant(rs, "resolved_at"), Rows.instant(rs, "observed_created_at"),
                Rows.instant(rs, "observed_modified_at"), rs.getString("observed_file_key"),
                IdentityEvidence.valueOf(rs.getString("identity_evidence")),
                rs.getString("decision_evidence") == null ? null : DecisionEvidence.valueOf(rs.getString("decision_evidence")),
                Rows.instant(rs, "decision_checked_at"));
    };

    private final JdbcClient jdbc;

    public UnresolvedObjectRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /**
     * Records the location as unresolved, unless it already has an open record (which is kept as it is).
     *
     * @return the open record for the location
     */
    public Entry recordIfAbsent(NewEntry e) {
        Identity o = e.observed();
        jdbc.sql("""
                INSERT INTO gas.unresolved_object (unresolved_id, volume_id, path, content_id, generation, operation_id, kind,
                                                   observed_size, observed_sha256, detail, observed_created_at,
                                                   observed_modified_at, observed_file_key, identity_evidence)
                VALUES (:id, :vol, :path, :content, :gen, :op, :kind, :size, :sha, :detail, :created, :modified, :key, :evidence)
                ON CONFLICT (volume_id, path) WHERE status = 'OPEN' DO NOTHING
                """).param("id", org.genealogyarchive.identity.GlobalIds.newId()).param("vol", e.volumeId())
                .param("path", e.path()).param("content", e.contentId()).param("gen", e.generation())
                .param("op", e.operationId()).param("kind", e.kind().name()).param("size", o == null ? null : o.size())
                .param("sha", o == null ? null : o.sha256()).param("detail", e.detail().length() > 4000
                        ? e.detail().substring(0, 4000) : e.detail())
                .param("created", o == null ? null : java.time.OffsetDateTime.ofInstant(o.created(), java.time.ZoneOffset.UTC))
                .param("modified", o == null ? null : java.time.OffsetDateTime.ofInstant(o.modified(), java.time.ZoneOffset.UTC))
                .param("key", o == null ? null : o.fileKey())
                .param("evidence", (o == null ? IdentityEvidence.NONE : IdentityEvidence.RECORDED).name()).update();
        return openAt(e.volumeId(), e.path()).orElseThrow();
    }

    public Optional<Entry> find(UUID unresolvedId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.unresolved_object WHERE unresolved_id = :id")
                .param("id", unresolvedId).query(ENTRY).optional();
    }

    public Optional<Entry> openAt(UUID volumeId, String path) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.unresolved_object WHERE volume_id = :v AND path = :p AND status = 'OPEN'")
                .param("v", volumeId).param("p", path).query(ENTRY).optional();
    }

    /** The latest record at a location, open or resolved. */
    public Optional<Entry> latestAt(UUID volumeId, String path) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM gas.unresolved_object WHERE volume_id = :v AND path = :p
                 ORDER BY detected_at DESC, unresolved_id DESC LIMIT 1
                """).param("v", volumeId).param("p", path).query(ENTRY).optional();
    }

    /** Every record at the location resolved this way (V0020 integration: their decision evidence is checked). */
    public List<Entry> resolvedAt(UUID volumeId, String path, Resolution resolution) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM gas.unresolved_object
                 WHERE volume_id = :v AND path = :p AND status = 'RESOLVED' AND resolution = :r
                 ORDER BY detected_at, unresolved_id
                """).param("v", volumeId).param("p", path).param("r", resolution.name()).query(ENTRY).list();
    }

    /** Whether a record at the location was resolved this way for an object with this SHA-256. */
    public boolean resolvedAs(UUID volumeId, String path, Resolution resolution, String observedSha256) {
        return jdbc.sql("""
                SELECT count(*) FROM gas.unresolved_object
                 WHERE volume_id = :v AND path = :p AND status = 'RESOLVED' AND resolution = :r AND observed_sha256 = :sha
                """).param("v", volumeId).param("p", path).param("r", resolution.name()).param("sha", observedSha256)
                .query(Long.class).single() > 0;
    }

    /**
     * The observed SHA-256 of every object an Administrator released at this location (NOT_ARCHIVAL). Non-empty
     * means a decision was made about the location: nothing there is deleted on the strength of a hash.
     */
    public List<String> releasedHashesAt(UUID volumeId, String path) {
        return jdbc.sql("""
                SELECT coalesce(observed_sha256, '') FROM gas.unresolved_object
                 WHERE volume_id = :v AND path = :p AND status = 'RESOLVED' AND resolution = 'NOT_ARCHIVAL'
                """).param("v", volumeId).param("p", path).query(String.class).list();
    }

    /**
     * Every record an Administrator released (NOT_ARCHIVAL) at this location, with what was observed when each object
     * was found: the evidence a later object must match before it is taken for the released one (fifth V0019 audit).
     */
    public List<Entry> releasedAt(UUID volumeId, String path) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM gas.unresolved_object
                 WHERE volume_id = :v AND path = :p AND status = 'RESOLVED' AND resolution = 'NOT_ARCHIVAL'
                 ORDER BY detected_at, unresolved_id
                """).param("v", volumeId).param("p", path).query(ENTRY).list();
    }

    /** Locks the record for the rest of the caller's transaction (decisions about it are serialised). */
    public Optional<Entry> lockForUpdate(UUID unresolvedId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.unresolved_object WHERE unresolved_id = :id FOR UPDATE")
                .param("id", unresolvedId).query(ENTRY).optional();
    }

    public List<Entry> openForContent(UUID contentId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.unresolved_object WHERE content_id = :c AND status = 'OPEN' ORDER BY detected_at")
                .param("c", contentId).query(ENTRY).list();
    }

    public List<Entry> openForOperation(UUID operationId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.unresolved_object WHERE operation_id = :o AND status = 'OPEN' ORDER BY detected_at")
                .param("o", operationId).query(ENTRY).list();
    }

    public List<Entry> open() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gas.unresolved_object WHERE status = 'OPEN' ORDER BY detected_at, unresolved_id")
                .query(ENTRY).list();
    }

    public List<String> openPaths(UUID volumeId) {
        return jdbc.sql("SELECT path FROM gas.unresolved_object WHERE volume_id = :v AND status = 'OPEN'")
                .param("v", volumeId).query(String.class).list();
    }

    public long openCount() {
        return jdbc.sql("SELECT count(*) FROM gas.unresolved_object WHERE status = 'OPEN'").query(Long.class).single();
    }

    /** @return true if the record was open and is now resolved */
    public boolean resolve(UUID unresolvedId, Resolution resolution, String detail, UUID resolvedBy) {
        return jdbc.sql("""
                UPDATE gas.unresolved_object
                   SET status = 'RESOLVED', resolution = :r, resolution_detail = :d, resolved_by = :by,
                       resolved_at = clock_timestamp()
                 WHERE unresolved_id = :id AND status = 'OPEN'
                """).param("r", resolution.name()).param("d", detail.length() > 4000 ? detail.substring(0, 4000) : detail)
                .param("by", resolvedBy).param("id", unresolvedId).update() == 1;
    }

    /**
     * An Administrator's release (NOT_ARCHIVAL), with whether the object examined for it was exactly the recorded one
     * (V0020). Only a CONFIRMED release identifies an object later.
     *
     * @return true if the record was open and is now resolved
     */
    public boolean release(UUID unresolvedId, String detail, UUID resolvedBy, DecisionEvidence evidence) {
        return jdbc.sql("""
                UPDATE gas.unresolved_object
                   SET status = 'RESOLVED', resolution = 'NOT_ARCHIVAL', resolution_detail = :d, resolved_by = :by,
                       resolved_at = clock_timestamp(),
                       decision_evidence = CASE WHEN identity_evidence = 'LEGACY' THEN NULL ELSE :e END,
                       decision_checked_at = CASE WHEN identity_evidence = 'LEGACY' THEN NULL ELSE clock_timestamp() END
                 WHERE unresolved_id = :id AND status = 'OPEN'
                """).param("d", detail.length() > 4000 ? detail.substring(0, 4000) : detail).param("by", resolvedBy)
                .param("e", evidence.name()).param("id", unresolvedId).update() == 1;
    }

    /**
     * Records an Administrator's attestation (once) that the observed object is the archive's. The caller has just
     * confirmed that the object at the location is exactly the recorded one (V0020); only RECORDED records qualify.
     */
    public boolean attest(UUID unresolvedId, UUID attestedBy) {
        return jdbc.sql("""
                UPDATE gas.unresolved_object SET attested_by = :by, attested_at = clock_timestamp(),
                       decision_evidence = 'CONFIRMED', decision_checked_at = clock_timestamp()
                 WHERE unresolved_id = :id AND status = 'OPEN' AND attested_at IS NULL AND identity_evidence = 'RECORDED'
                """).param("by", attestedBy).param("id", unresolvedId).update() == 1;
    }
}
