package org.genealogyarchive.web;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.records.RecordsServices;
import org.genealogyarchive.records.Vocabulary;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Record classifications and Role types (D3-08): Administrators add, relabel and retire terms. Codes are permanent
 * and never deleted or reused; a retired term stays valid where it is used but is not offered for new use.
 */
@Controller
public class VocabularyController {

    private final RecordsServices records;
    private final WebActors actors;

    public VocabularyController(RecordsServices records, WebActors actors) {
        this.records = records;
        this.actors = actors;
    }

    @GetMapping("/vocabularies")
    public String list(Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        AccessPolicy.require(actor, Permission.MANAGE_VOCABULARIES);
        model.addAttribute("classifications", records.vocabularies().terms(actor, Vocabulary.CLASSIFICATION));
        model.addAttribute("roles", records.vocabularies().terms(actor, Vocabulary.ROLE_TYPE));
        return "vocabularies";
    }

    @PostMapping("/vocabularies/{vocabulary}")
    public String add(@PathVariable Vocabulary vocabulary, @RequestParam String code, @RequestParam String label,
                      @RequestParam(required = false) String description, Authentication authentication) {
        records.vocabularies().add(actors.require(authentication), vocabulary, code, label, description);
        return "redirect:/vocabularies";
    }

    @PostMapping("/vocabularies/{vocabulary}/{code}")
    public String correct(@PathVariable Vocabulary vocabulary, @PathVariable String code, @RequestParam long version,
                          @RequestParam String label, @RequestParam(required = false) String description,
                          @RequestParam(defaultValue = "false") boolean retired, Authentication authentication) {
        records.vocabularies().correct(actors.require(authentication), vocabulary, code, version, label, description, retired);
        return "redirect:/vocabularies";
    }
}
