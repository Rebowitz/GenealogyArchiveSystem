package org.genealogyarchive.web;

import jakarta.servlet.http.HttpServletRequest;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.archive.ArchiveService;
import org.genealogyarchive.preservation.FileOperation;
import org.genealogyarchive.preservation.IngestionService;
import org.genealogyarchive.preservation.PreservationServices;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Administrator preservation page: volume markers and free space, the ingestion destination,
 * integrity scans and findings, file operations (retry/cancel) and verified repair.
 */
@Controller
public class PreservationController {

    private final PreservationServices preservation;
    private final ArchiveService archives;
    private final WebActors actors;

    public PreservationController(PreservationServices preservation, ArchiveService archives, WebActors actors) {
        this.preservation = preservation;
        this.archives = archives;
        this.actors = actors;
    }

    @GetMapping("/preservation")
    public String view(Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("status", preservation.operations().status(actor, preservation.volumes()));
        model.addAttribute("scans", preservation.integrity().recentScans(actor, 20));
        model.addAttribute("findings", preservation.integrity().openFindings(actor, 200));
        model.addAttribute("operations", preservation.operations().recent(actor, 100));
        model.addAttribute("archive", archives.view(actor));
        return "preservation";
    }

    @PostMapping("/preservation/ingestion-volume")
    public String setIngestionVolume(@RequestParam long version, @RequestParam UUID volumeId,
                                     @RequestParam(required = false) String reason, Authentication authentication) {
        preservation.volumes().setIngestionVolume(actors.require(authentication), version, volumeId, reason);
        return "redirect:/preservation";
    }

    @PostMapping("/preservation/scans")
    public String requestScan(@RequestParam String kind, Authentication authentication) {
        preservation.integrity().requestScan(actors.require(authentication), kind);
        return "redirect:/preservation";
    }

    @GetMapping("/preservation/scans/{scanId}")
    public String scan(@PathVariable UUID scanId, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("scan", preservation.integrity().scan(actor, scanId));
        model.addAttribute("findings", preservation.integrity().findingsOfScan(actor, scanId));
        return "scan";
    }

    @PostMapping("/preservation/contents/{contentId}/reverify")
    public String reverify(@PathVariable UUID contentId, Model model, Authentication authentication) {
        boolean ok = preservation.integrity().reverify(actors.require(authentication), contentId);
        if (!ok) {
            throw new RuleViolationException("The stored original does not match its authoritative size and SHA-256 "
                    + "(or is missing). It stays recorded as a problem; use verified repair with a good copy.");
        }
        return "redirect:/preservation";
    }

    @GetMapping("/preservation/operations/{operationId}")
    public String operation(@PathVariable UUID operationId, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("op", preservation.operations().get(actor, operationId));
        model.addAttribute("events", preservation.operations().events(actor, operationId));
        return "operation";
    }

    @PostMapping("/preservation/operations/{operationId}/retry")
    public String retry(@PathVariable UUID operationId, Authentication authentication) {
        preservation.operations().retry(actors.require(authentication), operationId);
        return "redirect:/preservation/operations/" + operationId;
    }

    @PostMapping("/preservation/operations/{operationId}/cancel")
    public String cancel(@PathVariable UUID operationId, Authentication authentication) {
        preservation.operations().cancel(actors.require(authentication), operationId);
        return "redirect:/preservation/operations/" + operationId;
    }

    @GetMapping("/preservation/contents/{contentId}/repair")
    public String repairPage(@PathVariable UUID contentId, Model model, Authentication authentication) {
        Actor actor = actors.require(authentication);
        model.addAttribute("contentId", contentId);
        model.addAttribute("findings", preservation.integrity().openFindings(actor, 200).stream()
                .filter(f -> contentId.equals(f.contentId())).toList());
        return "repair";
    }

    /** Receives replacement bytes (raw body, like uploads). */
    @PutMapping("/preservation/contents/{contentId}/repair")
    @ResponseBody
    public ResponseEntity<String> repair(@PathVariable UUID contentId, HttpServletRequest request,
                                         Authentication authentication) throws IOException {
        Actor actor = actors.require(authentication);
        try {
            String name = request.getHeader("X-File-Name");
            FileOperation op = preservation.repair().receive(actor, contentId,
                    name == null ? null : URLDecoder.decode(name, StandardCharsets.UTF_8),
                    request.getContentLengthLong(), request.getInputStream());
            return FileController.json(HttpStatus.OK, FileController.status(op));
        } catch (IngestionService.UploadTooLargeException e) {
            return message(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage());
        } catch (NotFoundException e) {
            return message(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (RuleViolationException e) {
            return message(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @GetMapping("/preservation/operations/{operationId}/status")
    @ResponseBody
    public ResponseEntity<String> repairStatus(@PathVariable UUID operationId, Authentication authentication) {
        try {
            return FileController.json(HttpStatus.OK,
                    FileController.status(preservation.operations().get(actors.require(authentication), operationId)));
        } catch (NotFoundException e) {
            return message(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private static ResponseEntity<String> message(HttpStatus status, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", text);
        return FileController.json(status, m);
    }
}
