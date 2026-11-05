package org.genealogyarchive.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.preservation.ArchivalFile;
import org.genealogyarchive.preservation.FileOperation;
import org.genealogyarchive.preservation.FileService;
import org.genealogyarchive.preservation.IngestionService;
import org.genealogyarchive.preservation.OperationState;
import org.genealogyarchive.preservation.PreservationServices;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Archival Files in the browser: list, detail, corrections, recoverable deletion, destruction,
 * uploads and downloads.
 * <p>
 * Uploads are raw request bodies ({@code PUT}, one File per request) streamed straight into staging,
 * so no multipart parser, temporary upload directory or framework size limit is involved; the
 * archive's own limit ({@code files.maxUploadBytes}) is enforced while the bytes arrive. Downloads
 * are always sent as attachments with {@code nosniff} and a sandboxing Content-Security-Policy, so an
 * uploaded HTML or SVG file can never run in the archive's origin.
 */
@Controller
public class FileController {

    private final PreservationServices preservation;
    private final HistoryService history;
    private final WebActors actors;

    public FileController(PreservationServices preservation, HistoryService history, WebActors actors) {
        this.preservation = preservation;
        this.history = history;
        this.actors = actors;
    }

    @GetMapping("/files")
    public String list(@RequestParam(defaultValue = "false") boolean deleted, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("files", preservation.files().list(actor, deleted, 200));
        model.addAttribute("showingDeleted", deleted && AccessPolicy.allows(actor, Permission.EDIT_FILES));
        model.addAttribute("canIngest", AccessPolicy.allows(actor, Permission.INGEST_FILES));
        model.addAttribute("canEdit", AccessPolicy.allows(actor, Permission.EDIT_FILES));
        return "files";
    }

    @GetMapping("/files/find")
    public String find(@RequestParam String id, Authentication authentication) {
        ArchivalFile f = preservation.files().getByFriendlyId(actors.require(authentication), id);
        return "redirect:/files/" + f.fileId();
    }

    @GetMapping("/files/{fileId}")
    public String detail(@PathVariable UUID fileId, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        FileService.FileView view = preservation.files().view(actor, fileId);
        model.addAttribute("view", view);
        model.addAttribute("f", view.file());
        model.addAttribute("history", preservation.files().history(actor, fileId, history));
        model.addAttribute("canEdit", AccessPolicy.allows(actor, Permission.EDIT_FILES));
        model.addAttribute("canIngest", AccessPolicy.allows(actor, Permission.INGEST_FILES));
        model.addAttribute("canDestroy", AccessPolicy.allows(actor, Permission.DESTROY_FILES));
        model.addAttribute("canManage", AccessPolicy.allows(actor, Permission.MANAGE_PRESERVATION));
        return "file";
    }

    @PostMapping("/files/{fileId}")
    public String correct(@PathVariable UUID fileId, @RequestParam long version, @RequestParam String managedFilename,
                          @RequestParam int displayRotation, @RequestParam Restriction restriction,
                          @RequestParam(required = false) String reason, Authentication authentication) {
        preservation.files().correct(actors.require(authentication), fileId, version, managedFilename, displayRotation,
                restriction, reason);
        return "redirect:/files/" + fileId;
    }

    @PostMapping("/files/{fileId}/delete")
    public String delete(@PathVariable UUID fileId, @RequestParam long version, @RequestParam(required = false) String reason,
                         Authentication authentication) {
        preservation.files().delete(actors.require(authentication), fileId, version, reason);
        return "redirect:/files/" + fileId;
    }

    @PostMapping("/files/{fileId}/restore")
    public String restore(@PathVariable UUID fileId, @RequestParam long version, @RequestParam(required = false) String reason,
                          Authentication authentication) {
        preservation.files().restore(actors.require(authentication), fileId, version, reason);
        return "redirect:/files/" + fileId;
    }

    @PostMapping("/files/{fileId}/destroy")
    public String destroy(@PathVariable UUID fileId, @RequestParam long version, @RequestParam String reason,
                          @RequestParam(required = false) String replacement, Authentication authentication) {
        Actor actor = actors.require(authentication);
        UUID replacementId = null;
        if (replacement != null && !replacement.isBlank()) {
            replacementId = preservation.files().getByFriendlyId(actor, replacement).fileId();
        }
        preservation.files().destroy(actor, fileId, version, reason, replacementId);
        return "redirect:/files/" + fileId;
    }

    /** The preservation original, as an attachment. */
    @GetMapping("/files/{fileId}/download")
    public void download(@PathVariable UUID fileId, HttpServletResponse response, Authentication authentication) throws IOException {
        try (FileService.Download d = preservation.files().download(actors.require(authentication), fileId)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setContentLengthLong(d.size());
            response.setHeader("Content-Disposition", "attachment; filename=\"" + asciiFallback(d.file().managedFilename())
                    + "\"; filename*=UTF-8''" + URLEncoder.encode(d.file().managedFilename(), StandardCharsets.UTF_8).replace("+", "%20"));
            safeHeaders(response, d.file().restricted());
            try (OutputStream out = response.getOutputStream()) {
                d.stream().transferTo(out);
            }
        }
    }

    @GetMapping("/files/{fileId}/thumbnail")
    public void thumbnail(@PathVariable UUID fileId, HttpServletResponse response, Authentication authentication) throws IOException {
        Actor actor = actors.require(authentication);
        var path = preservation.files().thumbnail(actor, fileId);
        if (path.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        ArchivalFile f = preservation.files().get(actor, fileId);
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        safeHeaders(response, f.restricted());
        try (InputStream in = Files.newInputStream(path.get()); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    // ---- uploads -------------------------------------------------------------------------------

    @GetMapping("/files/upload")
    public String uploadPage(@RequestParam(required = false) UUID parent, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("readiness", preservation.ingestion().readiness(actor));
        if (parent != null) {
            model.addAttribute("parent", preservation.files().get(actor, parent));
        }
        return "upload";
    }

    @PostMapping("/files/batches")
    public String startBatch(@RequestParam(required = false) String note, @RequestParam(required = false) UUID parent,
                             Authentication authentication) {
        var batch = preservation.ingestion().startBatch(actors.require(authentication), note);
        return "redirect:/files/batches/" + batch.batchId() + (parent == null ? "" : "?parent=" + parent);
    }

    @GetMapping("/files/batches/{batchId}")
    public String batchPage(@PathVariable UUID batchId, @RequestParam(required = false) UUID parent, Model model,
                            Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("batch", preservation.ingestion().batch(actor, batchId));
        model.addAttribute("items", preservation.ingestion().batchItems(actor, batchId));
        model.addAttribute("maxUploadBytes", preservation.context().settings().maxUploadBytes());
        if (parent != null) {
            model.addAttribute("parent", preservation.files().get(actor, parent));
        }
        return "batch";
    }

    /**
     * Receives one file as the raw request body. Provenance travels in headers: {@code X-File-Name}
     * (percent-encoded UTF-8), {@code X-File-Last-Modified} (epoch milliseconds), {@code X-Restriction},
     * {@code X-Source-Note}, {@code X-Parent-File} and {@code X-Derivation-Method} (percent-encoded).
     */
    @PutMapping("/files/batches/{batchId}/items/{itemKey}")
    @ResponseBody
    public ResponseEntity<String> upload(@PathVariable UUID batchId, @PathVariable String itemKey,
                                                      HttpServletRequest request, Authentication authentication) throws IOException {
        Actor actor = actors.require(authentication);
        try {
            IngestionService.Upload upload = new IngestionService.Upload(batchId, itemKey,
                    decode(request.getHeader("X-File-Name")), epochMillis(request.getHeader("X-File-Last-Modified")),
                    "RESTRICTED".equals(request.getHeader("X-Restriction")) ? Restriction.RESTRICTED : Restriction.NORMAL,
                    decode(request.getHeader("X-Source-Note")), uuid(request.getHeader("X-Parent-File")),
                    decode(request.getHeader("X-Derivation-Method")));
            FileOperation op = preservation.ingestion().receive(actor, upload, request.getContentLengthLong(), request.getInputStream());
            return json(HttpStatus.OK, status(op));
        } catch (IngestionService.UploadTooLargeException e) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage());
        } catch (NotFoundException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (RuleViolationException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    /** Progress of an upload as seen by the browser; "safely archived" only once COMPLETED/COMMITTED. */
    @GetMapping("/files/operations/{operationId}/status")
    @ResponseBody
    public ResponseEntity<String> operationStatus(@PathVariable UUID operationId, Authentication authentication) {
        try {
            return json(HttpStatus.OK, status(preservation.ingestion().operation(actors.require(authentication), operationId)));
        } catch (NotFoundException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    static Map<String, Object> status(FileOperation op) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operationId", op.operationId().toString());
        m.put("state", op.state().name());
        boolean archived = op.state() == OperationState.COMMITTED || op.state() == OperationState.COMPLETED;
        m.put("archived", archived);
        m.put("fileId", op.fileId() == null ? null : op.fileId().toString());
        m.put("outcome", op.outcome());
        m.put("message", switch (op.state()) {
            case RECEIVING -> "Receiving";
            case STAGED, PROMOTING, PROMOTED -> "Verifying";
            case COMMITTED, COMPLETED -> "SHARED_CONTENT".equals(op.outcome())
                    ? "Safely archived (identical bytes already stored; new File and provenance recorded)" : "Safely archived";
            case WAITING_FOR_VOLUME -> "Waiting: the storage volume is not available. Nothing is lost; it continues when the volume returns.";
            case BLOCKED -> "Stopped for an Administrator to check: " + op.message();
            case ABANDONED, CANCELLED, REJECTED -> "Not archived: " + (op.message() == null ? op.state().name() : op.message());
            default -> op.state().name();
        });
        return m;
    }

    private static ResponseEntity<String> error(HttpStatus status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return json(status, m);
    }

    /** Minimal JSON for the two upload endpoints (flat string/boolean/null values only). */
    static ResponseEntity<String> json(HttpStatus status, Map<String, Object> values) {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append(quote(e.getKey())).append(':');
            Object v = e.getValue();
            sb.append(v == null ? "null" : v instanceof Boolean b ? b.toString() : quote(v.toString()));
        }
        sb.append('}');
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(sb.toString());
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    static void safeHeaders(HttpServletResponse response, boolean restricted) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        response.setHeader("Cache-Control", restricted ? "no-store" : "private, max-age=0, must-revalidate");
    }

    private static String asciiFallback(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            sb.append(c >= 0x20 && c < 0x7F && c != '"' && c != '\\' ? c : '_');
        }
        return sb.toString();
    }

    private static String decode(String header) {
        return header == null ? null : URLDecoder.decode(header, StandardCharsets.UTF_8);
    }

    private static Instant epochMillis(String header) {
        try {
            return header == null || header.isBlank() ? null : Instant.ofEpochMilli(Long.parseLong(header.strip()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID uuid(String header) {
        try {
            return header == null || header.isBlank() ? null : UUID.fromString(header.strip());
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("The parent file");
        }
    }
}
