package org.genealogyarchive.web;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.jobs.JobQueue;
import org.genealogyarchive.preservation.PreservationServices;
import org.genealogyarchive.service.OperationalStatusService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/** Administrator operational view: volume availability, worker presence, background jobs, preservation summary. */
@Controller
public class OperationsController {

    private final OperationalStatusService status;
    private final JobQueue jobs;
    private final WebActors actors;
    private final PreservationServices preservation;

    public OperationsController(OperationalStatusService status, JobQueue jobs, WebActors actors,
                                PreservationServices preservation) {
        this.status = status;
        this.jobs = jobs;
        this.actors = actors;
        this.preservation = preservation;
    }

    @GetMapping("/operations")
    public String view(Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("ops", status.view(actor));
        model.addAttribute("jobs", jobs.recent(actor, 100));
        model.addAttribute("preservation", preservation.operations().status(actor, preservation.volumes()));
        // Phase 2 requirement 5: confirm from THIS (web) process that archival originals are read-only to it.
        model.addAttribute("webWriteProblems", preservation.volumes().verifyNoWriteAccess());
        return "operations";
    }

    @PostMapping("/operations/jobs/{jobId}/retry")
    public String retry(@PathVariable UUID jobId, Authentication authentication) {
        jobs.retry(actors.require(authentication), jobId);
        return "redirect:/operations";
    }
}
