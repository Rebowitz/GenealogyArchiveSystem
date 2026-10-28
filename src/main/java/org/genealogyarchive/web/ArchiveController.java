package org.genealogyarchive.web;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.archive.ArchiveService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Archive identity and archive-level configuration. */
@Controller
public class ArchiveController {

    private final ArchiveService archives;
    private final WebActors actors;

    public ArchiveController(ArchiveService archives, WebActors actors) {
        this.archives = archives;
        this.actors = actors;
    }

    @GetMapping("/archive")
    public String view(Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("archive", archives.view(actor));
        model.addAttribute("configuration", archives.configuration(actor));
        model.addAttribute("history", archives.history(actor));
        model.addAttribute("canConfigure", AccessPolicy.allows(actor, Permission.CONFIGURE_ARCHIVE));
        return "archive";
    }

    @PostMapping("/archive")
    public String rename(@RequestParam long version, @RequestParam String archiveName,
                         @RequestParam(required = false) String reason, Authentication authentication) {
        archives.rename(actors.require(authentication), version, archiveName, reason);
        return "redirect:/archive";
    }
}
