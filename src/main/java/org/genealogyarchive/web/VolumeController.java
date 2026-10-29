package org.genealogyarchive.web;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.storage.StorageVolumeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/** Storage Volume registration and description. Phase 1 writes no archival Files. */
@Controller
public class VolumeController {

    private final StorageVolumeService volumes;
    private final HistoryService history;
    private final WebActors actors;

    public VolumeController(StorageVolumeService volumes, HistoryService history, WebActors actors) {
        this.volumes = volumes;
        this.history = history;
        this.actors = actors;
    }

    @GetMapping("/volumes")
    public String list(Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("volumes", volumes.list(actor));
        model.addAttribute("canManage", AccessPolicy.allows(actor, Permission.MANAGE_STORAGE_VOLUMES));
        return "volumes";
    }

    @PostMapping("/volumes")
    public String register(@RequestParam String name, @RequestParam String purpose,
                           @RequestParam(required = false) String description,
                           @RequestParam(defaultValue = "false") boolean required, Authentication authentication) {
        var v = volumes.register(actors.require(authentication), name, purpose, description, required);
        return "redirect:/volumes/" + v.volumeId();
    }

    @GetMapping("/volumes/{volumeId}")
    public String detail(@PathVariable UUID volumeId, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("volume", volumes.get(actor, volumeId));
        model.addAttribute("canManage", AccessPolicy.allows(actor, Permission.MANAGE_STORAGE_VOLUMES));
        model.addAttribute("history", history.forObject(actor, StorageVolumeService.OBJECT_TYPE, volumeId));
        return "volume";
    }

    @PostMapping("/volumes/{volumeId}")
    public String correct(@PathVariable UUID volumeId, @RequestParam long version, @RequestParam String name,
                          @RequestParam String purpose, @RequestParam(required = false) String description,
                          @RequestParam(defaultValue = "false") boolean required,
                          @RequestParam(required = false) String reason, Authentication authentication) {
        volumes.correct(actors.require(authentication), volumeId, version, name, purpose, description, required, reason);
        return "redirect:/volumes/" + volumeId;
    }

    @PostMapping("/volumes/{volumeId}/retire")
    public String retire(@PathVariable UUID volumeId, @RequestParam long version,
                         @RequestParam(required = false) String reason, Authentication authentication) {
        volumes.retire(actors.require(authentication), volumeId, version, reason);
        return "redirect:/volumes/" + volumeId;
    }
}
