package org.genealogyarchive.web;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.data.CaptureSessionRepository;
import org.genealogyarchive.data.CaptureSessionRepository.FieldOverride;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.preservation.PreservationServices;
import org.genealogyarchive.records.CaptureSession;
import org.genealogyarchive.records.CaptureSessionService;
import org.genealogyarchive.records.GroupingRun;
import org.genealogyarchive.records.OverrideMode;
import org.genealogyarchive.records.RecordsServices;
import org.genealogyarchive.records.Vocabulary;
import org.genealogyarchive.service.RuleViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Capture Sessions (BUILD-SPEC s.38; BI-001, R-004, R-005) in the browser: the session's inherited context, adding
 * Files, the courthouse worksheet (D3-02), applying it as a grouping run (ADR-0011 s.4) and each File's override of
 * the inherited context (tri-state, plan section 10.2). Worksheet rows are numbered by position among the rows the
 * reader may see, never by stored sequence (ADR-0011 s.8).
 */
@Controller
public class CaptureSessionController {

    /** Rows per worksheet page (one small save transaction per page, R-005). */
    static final int PAGE = 100;

    private final RecordsServices records;
    private final PreservationServices preservation;
    private final HistoryService history;
    private final WebActors actors;

    public CaptureSessionController(RecordsServices records, PreservationServices preservation, HistoryService history,
                                    WebActors actors) {
        this.records = records;
        this.preservation = preservation;
        this.history = history;
        this.actors = actors;
    }

    @GetMapping("/sessions")
    public String list(Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("sessions", records.sessions().list(actor, null));
        model.addAttribute("repositories", records.catalog().repositories(actor));
        model.addAttribute("sources", records.catalog().sources(actor, null));
        model.addAttribute("canEdit", AccessPolicy.allows(actor, Permission.EDIT_RECORDS));
        return "sessions";
    }

    @PostMapping("/sessions")
    public String create(@RequestParam String title, @RequestParam(required = false) UUID repositoryId,
                         @RequestParam(required = false) UUID sourceId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate captureDate,
                         @RequestParam(required = false) String researcher,
                         @RequestParam(required = false) String equipmentMethod,
                         @RequestParam(required = false) String notes, Authentication authentication) {
        CaptureSession s = records.sessions().create(actors.require(authentication), new CaptureSessionService.SessionInput(
                title, repositoryId, sourceId, captureDate, researcher, equipmentMethod, notes));
        return "redirect:/sessions/" + s.captureSessionId();
    }

    @GetMapping("/sessions/find")
    public String find(@RequestParam String id, Authentication authentication) {
        return "redirect:/sessions/" + records.sessions().getByFriendlyId(actors.require(authentication), id).captureSessionId();
    }

    @GetMapping("/sessions/{id}")
    public String detail(@PathVariable UUID id, @RequestParam(defaultValue = "0") int offset, Model model,
                         Authentication authentication) {
        Actor actor = actors.require(authentication);
        CaptureSessionService.Worksheet sheet = records.sessions().worksheet(actor, id, offset, PAGE);
        CaptureSession s = sheet.session();
        model.addAttribute("s", s);
        model.addAttribute("sheet", sheet);
        model.addAttribute("repository", s.repositoryId() == null ? null
                : records.catalog().findRepository(actor, s.repositoryId()).orElse(null));
        model.addAttribute("source", s.sourceId() == null ? null : records.catalog().findSource(actor, s.sourceId()).orElse(null));
        model.addAttribute("repositories", records.catalog().repositories(actor));
        model.addAttribute("sources", records.catalog().sources(actor, null));
        model.addAttribute("classifications", records.vocabularies().active(actor, Vocabulary.CLASSIFICATION));
        model.addAttribute("classificationLabels", records.vocabularies().labels(actor, Vocabulary.CLASSIFICATION));
        model.addAttribute("runs", records.grouping().runs(actor, id));
        model.addAttribute("history", records.sessions().history(actor, id, history));
        boolean canEdit = AccessPolicy.allows(actor, Permission.EDIT_RECORDS);
        model.addAttribute("canEdit", canEdit);
        model.addAttribute("batches", canEdit ? records.sessions().recentBatches(actor) : List.of());
        model.addAttribute("page", PAGE);
        return "session";
    }

    @PostMapping("/sessions/{id}")
    public String correct(@PathVariable UUID id, @RequestParam long version, @RequestParam String title,
                          @RequestParam(required = false) UUID repositoryId, @RequestParam(required = false) UUID sourceId,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate captureDate,
                          @RequestParam(required = false) String researcher,
                          @RequestParam(required = false) String equipmentMethod,
                          @RequestParam(required = false) String notes, @RequestParam(required = false) String reason,
                          Authentication authentication) {
        records.sessions().correct(actors.require(authentication), id, version, new CaptureSessionService.SessionInput(
                title, repositoryId, sourceId, captureDate, researcher, equipmentMethod, notes), reason);
        return "redirect:/sessions/" + id;
    }

    @PostMapping("/sessions/{id}/files")
    public String addFiles(@PathVariable UUID id, @RequestParam(required = false) UUID batchId,
                           @RequestParam(required = false) String fileIds, RedirectAttributes flash,
                           Authentication authentication) {
        Actor actor = actors.require(authentication);
        CaptureSessionService.AddResult r = batchId != null
                ? records.sessions().addBatch(actor, id, batchId)
                : records.sessions().addByFriendlyIds(actor, id, fileIds);
        flash.addFlashAttribute("notice", r.added() + " added; " + r.alreadyInASession() + " already in a capture session; "
                + r.notAvailable() + " not available.");
        return "redirect:/sessions/" + id;
    }

    @PostMapping("/sessions/{id}/files/{fileId}/remove")
    public String removeFile(@PathVariable UUID id, @PathVariable UUID fileId, @RequestParam long version,
                             @RequestParam(required = false) String reason, Authentication authentication) {
        records.sessions().removeFile(actors.require(authentication), fileId, version, reason);
        return "redirect:/sessions/" + id;
    }

    /** Saves one worksheet page's marks (R-005: partial progress is ordinary saved state). */
    @PostMapping("/sessions/{id}/worksheet")
    public String saveWorksheet(@PathVariable UUID id, @RequestParam(defaultValue = "0") int offset,
                                @RequestParam(name = "row", required = false) List<UUID> rows,
                                @RequestParam Map<String, String> fields,
                                @RequestParam(name = "start", required = false) List<UUID> starts,
                                RedirectAttributes flash, Authentication authentication) {
        Set<UUID> start = starts == null ? Set.of() : new HashSet<>(starts);
        List<CaptureSessionService.MarkEdit> edits = new ArrayList<>();
        for (UUID fileId : rows == null ? List.<UUID>of() : rows) {
            long version;
            try {
                version = Long.parseLong(fields.getOrDefault("version_" + fileId, ""));
            } catch (NumberFormatException e) {
                throw new RuleViolationException("The worksheet form is incomplete; reload the page.");
            }
            edits.add(new CaptureSessionService.MarkEdit(fileId, version, start.contains(fileId),
                    fields.get("group_" + fileId), fields.get("page_" + fileId), fields.get("class_" + fileId)));
        }
        int n = records.sessions().saveMarks(actors.require(authentication), id, edits);
        flash.addFlashAttribute("notice", n + " worksheet row(s) saved.");
        return "redirect:/sessions/" + id + "?offset=" + offset;
    }

    @PostMapping("/sessions/{id}/apply")
    public String apply(@PathVariable UUID id, @RequestParam(required = false) String through, RedirectAttributes flash,
                        Authentication authentication) {
        Actor actor = actors.require(authentication);
        UUID throughFileId = through == null || through.isBlank() ? null
                : preservation.files().getByFriendlyId(actor, through).fileId();
        GroupingRun run = records.grouping().apply(actor, id, throughFileId);
        StringBuilder sb = new StringBuilder("Grouping applied: ").append(run.recordsCreated()).append(" Record(s) created, ")
                .append(run.recordsExtended()).append(" extended, ").append(run.rowsApplied()).append(" row(s) applied");
        if (run.rowsUngrouped() > 0) {
            sb.append("; ").append(run.rowsUngrouped()).append(" row(s) ungrouped (no Start New Record or group before them)");
        }
        if (run.rowsHeld() > 0) {
            sb.append("; ").append(run.rowsHeld()).append(" row(s) in ").append(run.groupsHeld())
                    .append(" group(s) held for an Administrator");
        }
        if (run.rowsConflicted() > 0) {
            sb.append("; ").append(run.rowsConflicted()).append(" row(s) changed during the run and were left for the next run");
        }
        flash.addFlashAttribute("notice", sb.append('.').toString());
        return "redirect:/sessions/" + id;
    }

    // ---- a File's capture context (shown and corrected on the File's page) -------------------------

    @PostMapping("/files/{fileId}/capture-context")
    public String correctOverrides(@PathVariable UUID fileId, @RequestParam long version,
                                   @RequestParam Map<String, String> f, Authentication authentication) {
        CaptureSessionRepository.Overrides o = new CaptureSessionRepository.Overrides(
                override(f, "repository", UUID::fromString),
                override(f, "source", UUID::fromString),
                override(f, "captureDate", LocalDate::parse),
                override(f, "researcher", Function.identity()),
                override(f, "equipmentMethod", Function.identity()));
        records.sessions().correctOverrides(actors.require(authentication), fileId, version, o, f.get("reason"));
        return "redirect:/files/" + fileId;
    }

    private static <T> FieldOverride<T> override(Map<String, String> f, String name, Function<String, T> parse) {
        OverrideMode mode;
        try {
            mode = OverrideMode.valueOf(f.getOrDefault(name + "Mode", "INHERIT"));
        } catch (IllegalArgumentException e) {
            throw new RuleViolationException("Unknown choice for " + name + ".");
        }
        if (mode != OverrideMode.VALUE) {
            return new FieldOverride<>(mode, null);
        }
        String text = f.get(name);
        if (text == null || text.isBlank()) {
            throw new RuleViolationException("Enter this File's own value, or choose \"inherit\" or \"not known for this File\".");
        }
        try {
            return FieldOverride.of(parse.apply(text.strip()));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new RuleViolationException("Not a valid value: " + text);
        }
    }
}
