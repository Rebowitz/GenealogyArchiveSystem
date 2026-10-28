package org.genealogyarchive.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.genealogyarchive.access.AccessDeniedException;
import org.genealogyarchive.security.AppUser;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Shared model attributes and researcher-facing error pages. Errors distinguish "not permitted",
 * "changed by someone else", "not found" and ordinary validation problems (BUILD-SPEC s.69); no
 * stack traces or technical details are shown.
 */
@ControllerAdvice
public class WebAdvice {

    private final WebActors actors;

    public WebAdvice(WebActors actors) {
        this.actors = actors;
    }

    @ModelAttribute("currentUser")
    public AppUser currentUser(Authentication authentication) {
        return actors.currentUser(authentication).orElse(null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String accessDenied(AccessDeniedException e, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return message(model, "Not permitted", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public String conflict(ConflictException e, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        return message(model, "Changed by someone else", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public String notFound(NotFoundException e, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return message(model, "Not found", e.getMessage());
    }

    @ExceptionHandler(RuleViolationException.class)
    public String rule(RuleViolationException e, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return message(model, "Not saved", e.getMessage());
    }

    @ExceptionHandler(WebActors.SessionEndedException.class)
    public String sessionEnded(HttpServletRequest request) throws ServletException {
        request.logout();
        return "redirect:/login?ended";
    }

    private static String message(Model model, String title, String text) {
        model.addAttribute("title", title);
        model.addAttribute("text", text);
        return "message";
    }
}
