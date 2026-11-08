package org.genealogyarchive.web;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.preservation.PreservationServices;
import org.genealogyarchive.preservation.Restriction;
import org.genealogyarchive.records.ArchivalRecord;
import org.genealogyarchive.records.Citation;
import org.genealogyarchive.records.RecordService;
import org.genealogyarchive.records.RecordsServices;
import org.genealogyarchive.records.Vocabulary;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Archival Records (BUILD-SPEC s.19; R-001...R-006) and Citations (s.17) in the browser. Every view is built from
 * the services' actor-filtered results: a Record shows only the Files the reader may see, numbered by their
 * position among those Files, never by stored sequence (ADR-0011 s.8).
 */
@Controller
public class RecordController {

    private final RecordsServices records;
    private final PreservationServices preservation;
    private final HistoryService history;
    private final WebActors actors;

    public RecordController(RecordsServices records, PreservationServices preservation, HistoryService history,
                            WebActors actors) {
        this.records = records;
        this.preservation = preservation;
        this.history = history;
        this.actors = actors;
    }

    @GetMapping("/records")
    public String list(@RequestParam(defaultValue = "false") boolean deleted,
                       @RequestParam(required = false) String classification, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("records", records.records().list(actor, deleted, classification, null, null, 500));
        model.addAttribute("classifications", records.vocabularies().terms(actor, Vocabulary.CLASSIFICATION));
        model.addAttribute("activeClassifications", records.vocabularies().active(actor, Vocabulary.CLASSIFICATION));
        model.addAttribute("labels", records.vocabularies().labels(actor, Vocabulary.CLASSIFICATION));
        model.addAttribute("sources", records.catalog().sources(actor, null));
        model.addAttribute("classification", classification);
        model.addAttribute("showingDeleted", deleted && AccessPolicy.allows(actor, Permission.EDIT_RECORDS));
        model.addAttribute("canEdit", AccessPolicy.allows(actor, Permission.EDIT_RECORDS));
        model.addAttribute("canRestrict", AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_RECORDS));
        return "records";
    }

    @PostMapping("/records")
    public String create(@RequestParam(required = false) String classificationCode,
                         @RequestParam(required = false) String summary, @RequestParam(required = false) UUID sourceId,
                         @RequestParam(required = false) String sourceLocator,
                         @RequestParam(required = false) String recordDateText,
                         @RequestParam(required = false) String filingDateText,
                         @RequestParam(required = false) String transcriptionText,
                         @RequestParam(required = false) String abstractText,
                         @RequestParam(required = false) Restriction restriction, Authentication authentication) {
        ArchivalRecord r = records.records().create(actors.require(authentication), new RecordService.RecordInput(
                classificationCode, summary, sourceId, sourceLocator, recordDateText, filingDateText, transcriptionText,
                abstractText, restriction));
        return "redirect:/records/" + r.recordId();
    }

    @GetMapping("/records/find")
    public String find(@RequestParam String id, Authentication authentication) {
        return "redirect:/records/" + records.records().getByFriendlyId(actors.require(authentication), id).recordId();
    }

    @GetMapping("/records/{id}")
    public String detail(@PathVariable UUID id, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        RecordService.RecordView view = records.records().view(actor, id);
        model.addAttribute("view", view);
        model.addAttribute("r", view.record());
        model.addAttribute("history", records.records().history(actor, id, history));
        model.addAttribute("activeClassifications", records.vocabularies().active(actor, Vocabulary.CLASSIFICATION));
        model.addAttribute("activeRoles", records.vocabularies().active(actor, Vocabulary.ROLE_TYPE));
        model.addAttribute("sources", records.catalog().sources(actor, null));
        model.addAttribute("canEdit", AccessPolicy.allows(actor, Permission.EDIT_RECORDS)
                && view.record().lifecycle().name().equals("ACTIVE"));
        model.addAttribute("canLifecycle", AccessPolicy.allows(actor, Permission.EDIT_RECORDS));
        model.addAttribute("canRestrict", AccessPolicy.allows(actor, Permission.VIEW_RESTRICTED_RECORDS));
        return "record";
    }

    @PostMapping("/records/{id}")
    public String correct(@PathVariable UUID id, @RequestParam long version,
                          @RequestParam(required = false) String classificationCode,
                          @RequestParam(required = false) String summary, @RequestParam(required = false) UUID sourceId,
                          @RequestParam(required = false) String sourceLocator,
                          @RequestParam(required = false) String recordDateText,
                          @RequestParam(required = false) String filingDateText,
                          @RequestParam(required = false) String transcriptionText,
                          @RequestParam(required = false) String abstractText,
                          @RequestParam(required = false) Restriction restriction,
                          @RequestParam(required = false) String reason, Authentication authentication) {
        records.records().correct(actors.require(authentication), id, version, new RecordService.RecordInput(
                classificationCode, summary, sourceId, sourceLocator, recordDateText, filingDateText, transcriptionText,
                abstractText, restriction), reason);
        return "redirect:/records/" + id;
    }

    @PostMapping("/records/{id}/delete")
    public String delete(@PathVariable UUID id, @RequestParam long version, @RequestParam(required = false) String reason,
                         Authentication authentication) {
        records.records().delete(actors.require(authentication), id, version, reason);
        return "redirect:/records/" + id;
    }

    @PostMapping("/records/{id}/restore")
    public String restore(@PathVariable UUID id, @RequestParam long version, @RequestParam(required = false) String reason,
                          Authentication authentication) {
        records.records().restore(actors.require(authentication), id, version, reason);
        return "redirect:/records/" + id;
    }

    // ---- File links ------------------------------------------------------------------------------

    @PostMapping("/records/{id}/files")
    public String link(@PathVariable UUID id, @RequestParam long version, @RequestParam String file,
                       @RequestParam(required = false) String page, @RequestParam(required = false) String note,
                       Authentication authentication) {
        Actor actor = actors.require(authentication);
        UUID fileId = preservation.files().getByFriendlyId(actor, file).fileId();
        records.records().linkFile(actor, id, version, fileId, page, note);
        return "redirect:/records/" + id;
    }

    @PostMapping("/records/{id}/files/{fileId}")
    public String correctLink(@PathVariable UUID id, @PathVariable UUID fileId, @RequestParam long version,
                              @RequestParam(required = false) String page, @RequestParam(required = false) String note,
                              Authentication authentication) {
        records.records().correctLink(actors.require(authentication), id, version, fileId, page, note);
        return "redirect:/records/" + id;
    }

    @PostMapping("/records/{id}/files/{fileId}/move")
    public String move(@PathVariable UUID id, @PathVariable UUID fileId, @RequestParam long version,
                       @RequestParam String direction, Authentication authentication) {
        records.records().moveLink(actors.require(authentication), id, version, fileId, "earlier".equals(direction));
        return "redirect:/records/" + id;
    }

    @PostMapping("/records/{id}/files/{fileId}/unlink")
    public String unlink(@PathVariable UUID id, @PathVariable UUID fileId, @RequestParam long version,
                         @RequestParam(required = false) String reason, Authentication authentication) {
        records.records().unlinkFile(actors.require(authentication), id, version, fileId, reason);
        return "redirect:/records/" + id;
    }

    // ---- Roles -----------------------------------------------------------------------------------

    @PostMapping("/records/{id}/roles")
    public String addRole(@PathVariable UUID id, @RequestParam long version, @RequestParam String roleCode,
                          @RequestParam(required = false) String party, @RequestParam(required = false) String note,
                          Authentication authentication) {
        records.records().addRole(actors.require(authentication), id, version, roleCode, party, note);
        return "redirect:/records/" + id;
    }

    @PostMapping("/records/{id}/roles/{roleId}")
    public String correctRole(@PathVariable UUID id, @PathVariable UUID roleId, @RequestParam long version,
                              @RequestParam long roleVersion, @RequestParam String roleCode,
                              @RequestParam(required = false) String party, @RequestParam(required = false) String note,
                              Authentication authentication) {
        records.records().correctRole(actors.require(authentication), roleId, version, roleVersion, roleCode, party, note);
        return "redirect:/records/" + id;
    }

    @PostMapping("/records/{id}/roles/{roleId}/remove")
    public String removeRole(@PathVariable UUID id, @PathVariable UUID roleId, @RequestParam long version,
                             @RequestParam long roleVersion, @RequestParam(required = false) String reason,
                             Authentication authentication) {
        records.records().removeRole(actors.require(authentication), roleId, version, roleVersion, reason);
        return "redirect:/records/" + id;
    }

    // ---- Citations -------------------------------------------------------------------------------

    @PostMapping("/citations")
    public String createCitation(@RequestParam(required = false) UUID sourceId, @RequestParam(required = false) UUID recordId,
                                 @RequestParam(required = false) String locator,
                                 @RequestParam(required = false) String citationText,
                                 @RequestParam(required = false) String note, Authentication authentication) {
        Citation c = records.citations().create(actors.require(authentication), sourceId, recordId, locator, citationText, note);
        return "redirect:/citations/" + c.citationId();
    }

    @GetMapping("/citations/{id}")
    public String citation(@PathVariable UUID id, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        Citation c = records.citations().get(actor, id);
        model.addAttribute("c", c);
        model.addAttribute("source", c.sourceId() == null ? null : records.catalog().findSource(actor, c.sourceId()).orElse(null));
        ArchivalRecord record = c.recordId() == null ? null : records.records().get(actor, c.recordId());
        model.addAttribute("record", record);
        // A Record-only Citation shows the Record's current Source "via the Record"; it is never stored (ADR-0011 s.6).
        model.addAttribute("viaSource", record == null || record.sourceId() == null ? null
                : records.catalog().findSource(actor, record.sourceId()).orElse(null));
        model.addAttribute("history", records.citations().history(actor, id, history));
        model.addAttribute("canEdit", AccessPolicy.allows(actor, Permission.EDIT_RECORDS));
        return "citation";
    }

    @PostMapping("/citations/{id}")
    public String correctCitation(@PathVariable UUID id, @RequestParam long version,
                                  @RequestParam(required = false) String locator,
                                  @RequestParam(required = false) String citationText,
                                  @RequestParam(required = false) String note,
                                  @RequestParam(defaultValue = "false") boolean deleted, Authentication authentication) {
        records.citations().correct(actors.require(authentication), id, version, locator, citationText, note, deleted);
        return "redirect:/citations/" + id;
    }
}
