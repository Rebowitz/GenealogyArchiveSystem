package org.genealogyarchive.web;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.jobs.JobQueue;
import org.genealogyarchive.service.OperationalStatusService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/** Administrator operational view: volume availability, worker presence, background jobs. */
@Controller
public class OperationsController {

    private final OperationalStatusService status;
    private final JobQueue jobs;
    private final WebActors actors;

    public OperationsController(OperationalStatusService status, JobQueue jobs, WebActors actors) {
        this.status = status;
        this.jobs = jobs;
        this.actors = actors;
    }

    @GetMapping("/operations")
    public String view(Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("ops", status.view(actor));
        model.addAttribute("jobs", jobs.recent(actor, 100));
        return "operations";
    }

    @PostMapping("/operations/jobs/{jobId}/retry")
    public String retry(@PathVariable UUID jobId, Authentication authentication) {
        jobs.retry(actors.require(authentication), jobId);
        return "redirect:/operations";
    }
}
