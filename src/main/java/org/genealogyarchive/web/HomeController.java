package org.genealogyarchive.web;

import org.genealogyarchive.archive.ArchiveService;
import org.genealogyarchive.service.SystemStatus;
import org.genealogyarchive.service.SystemStatusService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Browser entry point. Anyone who can reach the server sees whether the instance is ready and why
 * not (operational state only: no archive content, identifiers or version details). Signed-in
 * users additionally see the archive identity and foundation details.
 * Web code talks to services, never directly to the database (enforced by ArchitectureTest).
 */
@Controller
public class HomeController {

    private final SystemStatusService statusService;
    private final ArchiveService archives;
    private final WebActors actors;

    public HomeController(SystemStatusService statusService, ArchiveService archives, WebActors actors) {
        this.statusService = statusService;
        this.archives = archives;
        this.actors = actors;
    }

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        SystemStatus status = statusService.check();
        model.addAttribute("status", status);
        boolean signedIn = actors.currentUser(authentication).isPresent();
        model.addAttribute("signedIn", signedIn);
        if (signedIn && status.ready()) {
            model.addAttribute("archive", archives.view(actors.require(authentication)));
        }
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
