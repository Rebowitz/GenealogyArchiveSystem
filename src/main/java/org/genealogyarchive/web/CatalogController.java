package org.genealogyarchive.web;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.records.CatalogService;
import org.genealogyarchive.records.Citation;
import org.genealogyarchive.records.RecordsServices;
import org.genealogyarchive.records.Repository;
import org.genealogyarchive.records.Source;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Repositories (custody, BUILD-SPEC s.22) and Sources/record books (s.19) in the browser. A Source's Repository is
 * optional (O-003); its historical creator, Access Provider and jurisdiction are kept as written in their own
 * fields, never in the Repository (O-004...O-006, D3-06).
 */
@Controller
public class CatalogController {

    private final RecordsServices records;
    private final HistoryService history;
    private final WebActors actors;

    public CatalogController(RecordsServices records, HistoryService history, WebActors actors) {
        this.records = records;
        this.history = history;
        this.actors = actors;
    }

    // ---- Repositories ------------------------------------------------------------------------------

    @GetMapping("/repositories")
    public String repositories(Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("repositories", records.catalog().repositories(actor));
        model.addAttribute("canEdit", AccessPolicy.allows(actor, Permission.EDIT_RECORDS));
        return "repositories";
    }

    @PostMapping("/repositories")
    public String createRepository(@RequestParam String name, @RequestParam(required = false) String description,
                                   @RequestParam(required = false) String location, Authentication authentication) {
        Repository r = records.catalog().createRepository(actors.require(authentication), name, description, location);
        return "redirect:/repositories/" + r.repositoryId();
    }

    @GetMapping("/repositories/find")
    public String findRepository(@RequestParam String id, Authentication authentication) {
        return "redirect:/repositories/" + records.catalog().repositoryByFriendlyId(actors.require(authentication), id).repositoryId();
    }

    @GetMapping("/repositories/{id}")
    public String repository(@PathVariable UUID id, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("r", records.catalog().repository(actor, id));
        model.addAttribute("sources", records.catalog().sources(actor, id));
        model.addAttribute("history", records.catalog().repositoryHistory(actor, id, history));
        model.addAttribute("canEdit", AccessPolicy.allows(actor, Permission.EDIT_RECORDS));
        return "repository";
    }

    @PostMapping("/repositories/{id}")
    public String correctRepository(@PathVariable UUID id, @RequestParam long version, @RequestParam String name,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) String location,
                                    @RequestParam(required = false) String reason, Authentication authentication) {
        records.catalog().correctRepository(actors.require(authentication), id, version, name, description, location, reason);
        return "redirect:/repositories/" + id;
    }

    // ---- Sources -----------------------------------------------------------------------------------

    @GetMapping("/sources")
    public String sources(@RequestParam(required = false) UUID repository, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("sources", records.catalog().sources(actor, repository));
        model.addAttribute("repositories", records.catalog().repositories(actor));
        model.addAttribute("canEdit", AccessPolicy.allows(actor, Permission.EDIT_RECORDS));
        return "sources";
    }

    @PostMapping("/sources")
    public String createSource(@RequestParam String title, @RequestParam(required = false) UUID repositoryId,
                               @RequestParam(required = false) String sourceType,
                               @RequestParam(required = false) String volumeDesignation,
                               @RequestParam(required = false) String creatorText,
                               @RequestParam(required = false) String accessProviderText,
                               @RequestParam(required = false) String jurisdictionText,
                               @RequestParam(required = false) String notes, Authentication authentication) {
        Source s = records.catalog().createSource(actors.require(authentication), new CatalogService.SourceInput(title,
                repositoryId, sourceType, volumeDesignation, creatorText, accessProviderText, jurisdictionText, notes));
        return "redirect:/sources/" + s.sourceId();
    }

    @GetMapping("/sources/find")
    public String findSource(@RequestParam String id, Authentication authentication) {
        return "redirect:/sources/" + records.catalog().sourceByFriendlyId(actors.require(authentication), id).sourceId();
    }

    @GetMapping("/sources/{id}")
    public String source(@PathVariable UUID id, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        Source s = records.catalog().source(actor, id);
        model.addAttribute("s", s);
        model.addAttribute("repository", s.repositoryId() == null ? null
                : records.catalog().findRepository(actor, s.repositoryId()).orElse(null));
        model.addAttribute("repositories", records.catalog().repositories(actor));
        model.addAttribute("sessions", records.sessions().list(actor, id));
        model.addAttribute("records", records.records().list(actor, false, null, id, null, 500));
        List<Citation> citations = records.citations().ofSource(actor, id);
        model.addAttribute("citations", citations);
        model.addAttribute("history", records.catalog().sourceHistory(actor, id, history));
        model.addAttribute("canEdit", AccessPolicy.allows(actor, Permission.EDIT_RECORDS));
        return "source";
    }

    @PostMapping("/sources/{id}")
    public String correctSource(@PathVariable UUID id, @RequestParam long version, @RequestParam String title,
                                @RequestParam(required = false) UUID repositoryId,
                                @RequestParam(required = false) String sourceType,
                                @RequestParam(required = false) String volumeDesignation,
                                @RequestParam(required = false) String creatorText,
                                @RequestParam(required = false) String accessProviderText,
                                @RequestParam(required = false) String jurisdictionText,
                                @RequestParam(required = false) String notes,
                                @RequestParam(required = false) String reason, Authentication authentication) {
        records.catalog().correctSource(actors.require(authentication), id, version, new CatalogService.SourceInput(title,
                repositoryId, sourceType, volumeDesignation, creatorText, accessProviderText, jurisdictionText, notes), reason);
        return "redirect:/sources/" + id;
    }
}
