package org.genealogyarchive.web;

import org.genealogyarchive.history.HistoryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Researcher-facing Change History: what changed, when, and by whom. */
@Controller
public class HistoryController {

    private final HistoryService history;
    private final WebActors actors;

    public HistoryController(HistoryService history, WebActors actors) {
        this.history = history;
        this.actors = actors;
    }

    @GetMapping("/history")
    public String recent(Model model, Authentication authentication) {
        model.addAttribute("history", history.recent(actors.require(authentication), 200));
        return "history";
    }
}
