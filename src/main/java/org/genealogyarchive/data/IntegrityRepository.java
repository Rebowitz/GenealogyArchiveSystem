package org.genealogyarchive.data;

import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.preservation.FindingKind;
import org.genealogyarchive.preservation.IntegrityFinding;
import org.genealogyarchive.preservation.IntegrityScan;
import org.genealogyarchive.preservation.Sha256;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** integrity_scan and integrity_finding (V0012). */
public final class IntegrityRepository {

    private static final String SCAN_COLUMNS = """
            scan_id, kind, state, requested_by, requested_at, started_at, finished_at, contents_checked,
            findings_count, message""";
    private static final String FINDING_COLUMNS = """
            finding_id, scan_id, kind, volume_id, content_id, generation, storage_path, operation_id, observed_size,
            observed_sha256, detail, found_at, status, resolution, resolved_at""";

    private static final RowMapper<IntegrityScan> SCAN = (rs, n) -> new IntegrityScan(Rows.uuid(rs, "scan_id"),
            rs.getString("kind"), rs.getString("state"), Rows.uuid(rs, "requested_by"), Rows.instant(rs, "requested_at"),
            Rows.instant(rs, "started_at"), Rows.instant(rs, "finished_at"), rs.getInt("contents_checked"),
            rs.getInt("findings_count"), rs.getString("message"));

    private static final RowMapper<IntegrityFinding> FINDING = (rs, n) -> {
        byte[] sha = rs.getBytes("observed_sha256");
        return new IntegrityFinding(Rows.uuid(rs, "finding_id"), Rows.uuid(rs, "scan_id"),
                FindingKind.valueOf(rs.getString("kind")), Rows.uuid(rs, "volume_id"), Rows.uuid(rs, "content_id"),
                (Integer) rs.getObject("generation"), rs.getString("storage_path"), Rows.uuid(rs, "operation_id"),
                (Long) rs.getObject("observed_size"), sha == null ? null : Sha256.hex(sha), rs.getString("detail"),
                Rows.instant(rs, "found_at"), "OPEN".equals(rs.getString("status")), rs.getString("resolution"),
                Rows.instant(rs, "resolved_at"));
    };

    /** Values of a new finding. */
    public record NewFinding(FindingKind kind, UUID volumeId, UUID contentId, Integer generation, String storagePath,
                             UUID operationId, Long observedSize, String observedSha256, String detail) {
    }

    private final JdbcClient jdbc;

    public IntegrityRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    public void insertScan(UUID scanId, String kind, UUID requestedBy) {
        jdbc.sql("INSERT INTO gas.integrity_scan (scan_id, kind, state, requested_by) VALUES (:id, :kind, 'QUEUED', :by)")
                .param("id", scanId).param("kind", kind).param("by", requestedBy).update();
    }

    public void startScan(UUID scanId) {
        jdbc.sql("UPDATE gas.integrity_scan SET state = 'RUNNING', started_at = clock_timestamp() WHERE scan_id = :id")
                .param("id", scanId).update();
    }

    public void finishScan(UUID scanId, String state, int checked, int findings, String message) {
        jdbc.sql("""
                UPDATE gas.integrity_scan
                   SET state = :state, finished_at = clock_timestamp(), contents_checked = :checked,
                       findings_count = :findings, message = :message
                 WHERE scan_id = :id
                """).param("state", state).param("checked", checked).param("findings", findings)
                .param("message", message).param("id", scanId).update();
    }

    public Optional<IntegrityScan> findScan(UUID scanId) {
        return jdbc.sql("SELECT " + SCAN_COLUMNS + " FROM gas.integrity_scan WHERE scan_id = :id").param("id", scanId)
                .query(SCAN).optional();
    }

    public List<IntegrityScan> recentScans(int limit) {
        return jdbc.sql("SELECT " + SCAN_COLUMNS + " FROM gas.integrity_scan ORDER BY requested_at DESC LIMIT :n")
                .param("n", limit).query(SCAN).list();
    }

    public Optional<IntegrityScan> lastCompleted() {
        return jdbc.sql("SELECT " + SCAN_COLUMNS + " FROM gas.integrity_scan WHERE state = 'COMPLETED' ORDER BY finished_at DESC LIMIT 1")
                .query(SCAN).optional();
    }

    /**
     * Records a finding unless an identical one (same kind, content, generation and path) is already
     * open, so repeated scans do not pile up duplicates.
     *
     * @return the new finding's ID, or empty if an identical finding was already open
     */
    public Optional<UUID> insertFindingIfNew(UUID scanId, NewFinding f) {
        Observation o = observe(scanId, f);
        return o.isNew() ? Optional.of(o.findingId()) : Optional.empty();
    }

    /** A finding as seen by one scan: newly opened, or already open from an earlier observation. */
    public record Observation(UUID findingId, boolean isNew) {
    }

    /**
     * Records that a scan observed a problem (GAS-P2-011): if an identical finding is already open it is
     * linked to this scan as still present; otherwise a new finding is opened. With a null scan (problems
     * noticed outside a scan) only the finding itself is recorded.
     */
    public Observation observe(UUID scanId, NewFinding f) {
        Optional<UUID> open = jdbc.sql("""
                SELECT finding_id FROM gas.integrity_finding
                 WHERE status = 'OPEN' AND kind = :kind
                   AND content_id IS NOT DISTINCT FROM CAST(:content AS uuid)
                   AND generation IS NOT DISTINCT FROM CAST(:gen AS integer)
                   AND storage_path IS NOT DISTINCT FROM CAST(:path AS text)
                   AND volume_id IS NOT DISTINCT FROM CAST(:vol AS uuid)
                   AND operation_id IS NOT DISTINCT FROM CAST(:op AS uuid)
                 ORDER BY found_at LIMIT 1
                """).param("kind", f.kind().name()).param("content", str(f.contentId()))
                .param("gen", f.generation()).param("path", f.storagePath()).param("vol", str(f.volumeId()))
                .param("op", str(f.operationId())).query((rs, n) -> Rows.uuid(rs, "finding_id")).optional();
        if (open.isPresent()) {
            observeExisting(scanId, open.get());
            return new Observation(open.get(), false);
        }
        UUID id = insertFinding(scanId, f);
        observeExisting(scanId, id);
        return new Observation(id, true);
    }

    public boolean observedBy(UUID scanId, UUID findingId) {
        return jdbc.sql("SELECT count(*) FROM gas.integrity_finding_observation WHERE scan_id = :s AND finding_id = :f")
                .param("s", scanId).param("f", findingId).query(Long.class).single() > 0;
    }

    /** Links an existing finding to a scan that saw it still present (no-op without a scan). */
    public void observeExisting(UUID scanId, UUID findingId) {
        if (scanId == null) {
            return;
        }
        jdbc.sql("""
                INSERT INTO gas.integrity_finding_observation (scan_id, finding_id) VALUES (:s, :f)
                ON CONFLICT (scan_id, finding_id) DO NOTHING
                """).param("s", scanId).param("f", findingId).update();
    }

    private UUID insertFinding(UUID scanId, NewFinding f) {
        UUID id = GlobalIds.newId();
        jdbc.sql("""
                INSERT INTO gas.integrity_finding (finding_id, scan_id, kind, volume_id, content_id, generation, storage_path,
                    operation_id, observed_size, observed_sha256, detail)
                VALUES (:id, :scan, :kind, :vol, :content, :gen, :path, :op, :size, :sha, :detail)
                """).param("id", id).param("scan", scanId).param("kind", f.kind().name()).param("vol", f.volumeId())
                .param("content", f.contentId()).param("gen", f.generation()).param("path", f.storagePath())
                .param("op", f.operationId()).param("size", f.observedSize())
                .param("sha", f.observedSha256() == null ? null : Sha256.bytes(f.observedSha256()))
                .param("detail", f.detail().length() > 4000 ? f.detail().substring(0, 4000) : f.detail())
                .update();
        return id;
    }

    public Optional<IntegrityFinding> findFinding(UUID findingId) {
        return jdbc.sql("SELECT " + FINDING_COLUMNS + " FROM gas.integrity_finding WHERE finding_id = :id")
                .param("id", findingId).query(FINDING).optional();
    }

    public List<IntegrityFinding> openFindings(int limit) {
        return jdbc.sql("SELECT " + FINDING_COLUMNS + " FROM gas.integrity_finding WHERE status = 'OPEN' ORDER BY found_at DESC LIMIT :n")
                .param("n", limit).query(FINDING).list();
    }

    public List<IntegrityFinding> openFindingsFor(UUID contentId) {
        return jdbc.sql("SELECT " + FINDING_COLUMNS + " FROM gas.integrity_finding WHERE status = 'OPEN' AND content_id = :c ORDER BY found_at")
                .param("c", contentId).query(FINDING).list();
    }

    /**
     * Every finding the scan observed: those it opened and those opened earlier that it found still
     * present (GAS-P2-011). A finding's own scan ID tells which scan first found it.
     */
    public List<IntegrityFinding> findingsOfScan(UUID scanId) {
        return jdbc.sql("""
                        SELECT i.finding_id, i.scan_id, i.kind, i.volume_id, i.content_id, i.generation, i.storage_path,
                               i.operation_id, i.observed_size, i.observed_sha256, i.detail, i.found_at, i.status,
                               i.resolution, i.resolved_at""" + " FROM gas.integrity_finding i JOIN gas.integrity_finding_observation o ON o.finding_id = i.finding_id"
                        + " WHERE o.scan_id = :s ORDER BY i.found_at")
                .param("s", scanId).query(FINDING).list();
    }

    public List<IntegrityFinding> findingsFor(UUID contentId) {
        return jdbc.sql("SELECT " + FINDING_COLUMNS + " FROM gas.integrity_finding WHERE content_id = :c ORDER BY found_at")
                .param("c", contentId).query(FINDING).list();
    }

    /** @return true if the finding was open and is now resolved */
    public boolean resolve(UUID findingId, String resolution, UUID operationId) {
        return jdbc.sql("""
                UPDATE gas.integrity_finding
                   SET status = 'RESOLVED', resolution = :res, resolved_at = clock_timestamp(),
                       resolved_by_operation = :op
                 WHERE finding_id = :id AND status = 'OPEN'
                """).param("res", resolution).param("op", operationId).param("id", findingId).update() == 1;
    }

    public long openCount() {
        return jdbc.sql("SELECT count(*) FROM gas.integrity_finding WHERE status = 'OPEN'").query(Long.class).single();
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }
}
