package com.smartscheduler.scheduler.controller;

import com.smartscheduler.scheduler.configuration.GoogleCalendarConfig;
import com.smartscheduler.scheduler.configuration.MicrosoftCalendarConfig;
import com.smartscheduler.scheduler.model.DelegateRequest;
import com.smartscheduler.scheduler.service.DelegateStore;
import com.smartscheduler.scheduler.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/delegates")
public class DelegateController {

    private static final Logger log = LoggerFactory.getLogger(DelegateController.class);

    private final DelegateStore delegateStore;
    private final EmailService emailService;
    private final GoogleCalendarConfig googleConfig;
    private final MicrosoftCalendarConfig microsoftConfig;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${delegate.magic-link-base}")
    private String magicLinkBase;

    @Value("${delegate.link-expiry-hours:48}")
    private long expiryHours;

    public DelegateController(DelegateStore delegateStore, EmailService emailService,
                              GoogleCalendarConfig googleConfig, MicrosoftCalendarConfig microsoftConfig) {
        this.delegateStore = delegateStore;
        this.emailService = emailService;
        this.googleConfig = googleConfig;
        this.microsoftConfig = microsoftConfig;
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestDelegateAccess(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String provider = body.getOrDefault("provider", "google");
        log.info("Delegate access request: email={}, provider={}", email, provider);

        String requesterEmail = "scheduler-app@localhost";
        DelegateRequest request = delegateStore.createRequest(requesterEmail, email, provider, expiryHours);
        String magicLink = magicLinkBase + "?token=" + request.getId();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", request.getId());
        response.put("status", request.getStatus());
        response.put("magicLink", magicLink);

        try {
            emailService.sendDelegateRequestEmail(email, requesterEmail, magicLink, expiryHours);
            response.put("emailSent", true);
        } catch (Exception e) {
            log.warn("Failed to send email, returning magic link for manual sharing: {}", e.getMessage());
            response.put("emailSent", false);
            response.put("emailError", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validate(@RequestParam("token") String token) {
        log.info("Validate endpoint called with token={}", token);
        DelegateRequest request = delegateStore.getRequest(token);

        if (request == null) {
            log.warn("Invalid delegate token: {}", token);
            return ResponseEntity.status(404).body(Map.of("error", "Invalid token"));
        }

        if (System.currentTimeMillis() > Long.parseLong(request.getExpiresAt())) {
            log.warn("Expired delegate token: {}", token);
            return ResponseEntity.status(410).body(Map.of("error", "Token expired"));
        }

        if (!"PENDING".equals(request.getStatus())) {
            log.warn("Token already used, status={}", request.getStatus());
            return ResponseEntity.status(410).body(Map.of("error", "Token already used"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requesterEmail", request.getRequesterEmail());
        result.put("delegateEmail", request.getDelegateEmail());
        result.put("provider", request.getProvider());
        result.put("status", request.getStatus());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestParam("token") String token) throws Exception {
        log.info("Authorize endpoint called with token={}", token);
        DelegateRequest request = delegateStore.getRequest(token);

        if (request == null) {
            log.warn("Invalid delegate token: {}", token);
            return ResponseEntity.badRequest().build();
        }

        if (System.currentTimeMillis() > Long.parseLong(request.getExpiresAt())) {
            log.warn("Expired delegate token: {}", token);
            request.setStatus("EXPIRED");
            return ResponseEntity.badRequest().build();
        }

        if (!"PENDING".equals(request.getStatus())) {
            log.warn("Token already used, status={}", request.getStatus());
            return ResponseEntity.badRequest().build();
        }

        String redirectUrl;
        if ("microsoft".equalsIgnoreCase(request.getProvider())) {
            redirectUrl = microsoftConfig.buildDelegateAuthorizationUrl(request.getDelegateEmail());
        } else {
            redirectUrl = googleConfig.buildDelegateAuthorizationUrl(request.getDelegateEmail());
        }

        log.info("Redirecting delegate {} to OAuth: provider={}", request.getDelegateEmail(), request.getProvider());
        return ResponseEntity.status(302).header("Location", redirectUrl).build();
    }

    @GetMapping("/callback/google")
    public ResponseEntity<Void> googleCallback(@RequestParam("code") String code, @RequestParam("state") String state) {
        String delegateEmail = state;
        log.info("Google delegate OAuth callback for: {}", delegateEmail);

        try {
            googleConfig.handleDelegateAuthorizationCode(code, delegateEmail);
            log.info("Google delegate token exchange success for: {}", delegateEmail);

            DelegateRequest request = delegateStore.findPendingByDelegateEmail(delegateEmail);
            if (request != null) {
                delegateStore.markAuthorized(request.getId());
                log.info("Delegate authorized successfully: {}", delegateEmail);
            } else {
                log.warn("No pending request found for delegate: {}", delegateEmail);
            }

            return ResponseEntity.status(302).header("Location", frontendUrl + "/auth/delegate/success").build();
        } catch (Exception e) {
            log.error("Google delegate callback failed for: {}", delegateEmail, e);
            return ResponseEntity.status(302).header("Location", frontendUrl + "/auth/delegate/error").build();
        }
    }

    @GetMapping("/callback/microsoft")
    public ResponseEntity<Void> microsoftCallback(@RequestParam("code") String code, @RequestParam("state") String state) {
        String delegateEmail = state;
        log.info("Microsoft delegate OAuth callback for: {}", delegateEmail);

        try {
            microsoftConfig.handleDelegateAuthorizationCode(code, delegateEmail);
            log.info("Microsoft delegate token exchange success for: {}", delegateEmail);

            DelegateRequest request = delegateStore.findPendingByDelegateEmail(delegateEmail);
            if (request != null) {
                delegateStore.markAuthorized(request.getId());
                log.info("Delegate authorized successfully: {}", delegateEmail);
            } else {
                log.warn("No pending request found for delegate: {}", delegateEmail);
            }

            return ResponseEntity.status(302).header("Location", frontendUrl + "/auth/delegate/success").build();
        } catch (Exception e) {
            log.error("Microsoft delegate callback failed for: {}", delegateEmail, e);
            return ResponseEntity.status(302).header("Location", frontendUrl + "/auth/delegate/error").build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listDelegates() {
        log.info("Listing authorized delegates");
        List<DelegateRequest> delegates = delegateStore.listAuthorizedDelegates();
        List<Map<String, Object>> result = delegates.stream().map(d -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("email", d.getDelegateEmail());
            entry.put("provider", d.getProvider());
            entry.put("authorizedAt", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
            return entry;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Void> revokeDelegate(@PathVariable String email) throws Exception {
        log.info("Revoking delegate access for: {}", email);
        delegateStore.removeDelegate(email);
        try {
            microsoftConfig.revokeDelegateAuthorization(email);
        } catch (Exception e) {
            log.debug("No Microsoft tokens to revoke for {}", email);
        }
        return ResponseEntity.noContent().build();
    }
}
