package org.genealogyarchive.testsupport;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.records.CaptureRow;
import org.genealogyarchive.records.CaptureSession;
import org.genealogyarchive.records.CaptureSessionService;
import org.genealogyarchive.records.GroupingFaults;
import org.genealogyarchive.records.GroupingService;
import org.genealogyarchive.records.RecordsServices;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 3 test support over {@link PreservationFixture}: real ingested Files (normal or restricted), capture
 * sessions and worksheet marks, plus synthetic File rows for very large sessions (grouping never reads stored
 * bytes, so rows sharing one verified content are enough to exercise more than 1,000 rows quickly). No real
 * genealogy data: every name and value is invented.
 */
public final class RecordsFixture implements AutoCloseable {

    public final PreservationFixture fx;
    public final Actor admin;
    public final Actor editor;
    public final Actor viewer;
    private int counter;

    private RecordsFixture(PreservationFixture fx) {
        this.fx = fx;
        this.admin = fx.admin;
        this.editor = fx.editor;
        this.viewer = fx.viewer;
    }

    public static RecordsFixture create(Path temp) throws IOException {
        return new RecordsFixture(PreservationFixture.create(temp));
    }

    public RecordsServices records() {
        return fx.services().records();
    }

    /** A grouping service with test fault points. */
    public GroupingService grouping(GroupingFaults faults) {
        RecordsServices r = records();
        return new GroupingService(r.context(), r.records(), r.sessions(), faults);
    }

    /** An ingested, verified File (NORMAL). */
    public ArchivalFile file() {
        counter++;
        return fx.ingest(editor, "capture-" + counter + ".tif", ("synthetic image " + counter).getBytes(StandardCharsets.UTF_8));
    }

    /** An ingested File that an Administrator then restricts. */
    public ArchivalFile restrictedFile() {
        ArchivalFile f = file();
        return fx.preservation().files().correct(admin, f.fileId(), f.version(), f.managedFilename(), 0,
                Restriction.RESTRICTED, "test restriction");
    }

    public List<ArchivalFile> files(int n) {
        List<ArchivalFile> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(file());
        }
        return out;
    }

    public CaptureSession session(String title) {
        return records().sessions().create(admin, new CaptureSessionService.SessionInput(title, null, null, null, null,
                null, null));
    }

    /** Adds Files (as an Administrator, so restricted Files can be added) in the given order. */
    public void add(CaptureSession s, List<ArchivalFile> files) {
        records().sessions().addFiles(admin, s.captureSessionId(), files.stream().map(ArchivalFile::fileId).toList());
    }

    public CaptureRow row(UUID fileId) {
        return records().context().sessions().row(fileId).orElseThrow();
    }

    /** Sets one row's worksheet marks as an Administrator. */
    public void mark(CaptureSession s, ArchivalFile f, boolean start, String group, String page, String classification) {
        CaptureRow r = row(f.fileId());
        records().sessions().saveMarks(admin, s.captureSessionId(), List.of(new CaptureSessionService.MarkEdit(
                f.fileId(), r.version(), start, group, page, classification)));
    }

    public void start(CaptureSession s, ArchivalFile f) {
        mark(s, f, true, null, null, null);
    }

    /**
     * Inserts {@code n} synthetic File rows that share the content of one real ingested File, each with its own
     * ingest operation and friendly number, as ingestion would. Returns their ids in friendly-number order.
     */
    public List<UUID> syntheticFiles(int n) {
        ArchivalFile template = file();
        JdbcClient jdbc = JdbcClient.create(fx.ds);
        String seq = jdbc.sql("SELECT sequence_name FROM gas.friendly_id_series WHERE object_type = 'archival_file'")
                .query(String.class).single();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(UUID.randomUUID());
        }
        String[] fileIds = ids.stream().map(UUID::toString).toArray(String[]::new);
        long first = jdbc.sql("SELECT nextval(CAST(:seq AS regclass))").param("seq", "gas." + seq).query(Long.class).single();
        jdbc.sql("SELECT setval(CAST(:seq AS regclass), :last)").param("seq", "gas." + seq).param("last", first + n - 1)
                .query(Long.class).single();
        // One statement: each File gets its own committed ingest operation and the next friendly number, in order.
        jdbc.sql("""
                WITH f AS (SELECT u AS file_id, ord, gen_random_uuid() AS op_id
                             FROM unnest(CAST(:ids AS uuid[])) WITH ORDINALITY AS t(u, ord)),
                     ops AS (INSERT INTO gas.file_operation (operation_id, kind, state, actor_kind, actor_label, volume_id)
                             SELECT op_id, 'INGEST', 'COMMITTED', 'SYSTEM', 'synthetic', :vol FROM f RETURNING operation_id)
                INSERT INTO gas.archival_file (file_id, friendly_number, content_id, ingest_operation_id, original_filename,
                    managed_filename, media_type, ingested_at, ingested_by)
                SELECT f.file_id, :first + f.ord - 1, :content, f.op_id, 'synthetic-' || f.ord || '.tif',
                       'synthetic-' || f.ord || '.tif', 'image/tiff', now(), :by
                  FROM f JOIN ops ON ops.operation_id = f.op_id
                """).param("ids", fileIds).param("vol", fx.volumeId).param("first", first)
                .param("content", template.contentId()).param("by", admin.userId()).update();
        return ids;
    }

    @Override
    public void close() {
        fx.close();
    }
}
