package org.genealogyarchive.web;

import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.history.HistoryService;
import org.genealogyarchive.security.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/** User administration (Administrators) and own-password change (everyone). */
@Controller
public class UserController {

    private final UserService users;
    private final HistoryService history;
    private final WebActors actors;

    public UserController(UserService users, HistoryService history, WebActors actors) {
        this.users = users;
        this.history = history;
        this.actors = actors;
    }

    @GetMapping("/users")
    public String list(Model model, Authentication authentication) {
        model.addAttribute("users", users.list(actors.require(authentication)));
        model.addAttribute("roles", Role.values());
        return "users";
    }

    @PostMapping("/users")
    public String create(@RequestParam String username, @RequestParam String displayName, @RequestParam Role role,
                         @RequestParam String password, Authentication authentication) {
        users.create(actors.require(authentication), username, displayName, role, password);
        return "redirect:/users";
    }

    @GetMapping("/users/{userId}")
    public String detail(@PathVariable UUID userId, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("user", users.get(actor, userId));
        model.addAttribute("roles", Role.values());
        model.addAttribute("history", history.forObject(actor, UserService.OBJECT_TYPE, userId));
        return "user";
    }

    @PostMapping("/users/{userId}")
    public String update(@PathVariable UUID userId, @RequestParam long version, @RequestParam String displayName,
                         @RequestParam Role role, @RequestParam(defaultValue = "false") boolean enabled,
                         Authentication authentication) {
        users.update(actors.require(authentication), userId, version, displayName, role, enabled);
        return "redirect:/users/" + userId;
    }

    @PostMapping("/users/{userId}/password")
    public String resetPassword(@PathVariable UUID userId, @RequestParam long version, @RequestParam String password,
                                Authentication authentication) {
        users.resetPassword(actors.require(authentication), userId, version, password);
        return "redirect:/users/" + userId;
    }

    @GetMapping("/account/password")
    public String passwordForm() {
        return "password";
    }

    @PostMapping("/account/password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
                                 Model model, Authentication authentication) {
        users.changeOwnPassword(actors.require(authentication), currentPassword, newPassword);
        model.addAttribute("title", "Password changed");
        model.addAttribute("text", "Your password has been changed.");
        return "message";
    }
}
