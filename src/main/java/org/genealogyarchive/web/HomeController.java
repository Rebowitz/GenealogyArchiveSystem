package org.genealogyarchive.web;

import org.genealogyarchive.service.SystemStatusService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Browser entry point. Phase 0 shows only foundation status; no historical domain UI exists yet.
 * Web code talks to services, never directly to the database (enforced by ArchitectureTest).
 */
@Controller
public class HomeController {

    private final SystemStatusService statusService;

    public HomeController(SystemStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("status", statusService.check());
        return "index";
    }
}
