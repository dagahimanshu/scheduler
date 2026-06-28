package com.smartscheduler.scheduler.controller;

import com.smartscheduler.scheduler.configuration.GoogleCalendarConfig;
import com.smartscheduler.scheduler.configuration.MicrosoftCalendarConfig;
import com.smartscheduler.scheduler.service.DelegateStore;
import com.smartscheduler.scheduler.service.EmailService;
import com.smartscheduler.scheduler.service.JwtService;
import io.jsonwebtoken.Claims;
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
    private final JwtService jwtService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url}")
    private String backendUrl;

    @Value("${delegate.link-expiry-hours:48}")
    private long expiryHours;

    public DelegateController(DelegateStore delegateStore, EmailService emailService,
                              GoogleCalendarConfig googleConfig, MicrosoftCalendarConfig microsoftConfig,
                              JwtService jwtService) {
        this.delegateStore = delegateStore;
        this.emailService = emailService;
        this.googleConfig = googleConfig;
        this.microsoftConfig = microsoftConfig;
        this.jwtService = jwtService;
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestDelegateAccess(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String provider = body.getOrDefault("provider", "google");

        String requesterEmail = currentUserEmail();
        log.info("Delegate access request: requester={}, delegate={}, provider={}", requesterEmail, email, provider);

        String token = jwtService.generateDelegateToken(requesterEmail, email, provider);
        String magicLink = backendUrl + "/delegates/authorize?token=" + token;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "sent");
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
        log.info("Validate endpoint called");
        try {
            Claims claims = jwtService.validateDelegateToken(token);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requesterEmail", claims.get("requester", String.class));
            result.put("delegateEmail", claims.getSubject());
            result.put("provider", claims.get("provider", String.class));
            result.put("status", "PENDING");
            return ResponseEntity.ok(result);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("Delegate token expired");
            return ResponseEntity.status(410).body(Map.of("error", "Token expired"));
        } catch (Exception e) {
            log.warn("Invalid delegate token: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", "Invalid token"));
        }
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestParam("token") String token) throws Exception {
        log.info("Authorize endpoint called");
        try {
            Claims claims = jwtService.validateDelegateToken(token);
            String provider = claims.get("provider", String.class);

            String redirectUrl;
            if ("microsoft".equalsIgnoreCase(provider)) {
                redirectUrl = microsoftConfig.buildDelegateAuthorizationUrl(token);
            } else {
                redirectUrl = googleConfig.buildDelegateAuthorizationUrl(token);
            }

            log.info("Redirecting delegate {} to OAuth: provider={}", claims.getSubject(), provider);
            return ResponseEntity.status(302).header("Location", redirectUrl).build();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("Delegate token expired during authorize");
            return ResponseEntity.status(302)
                    .header("Location", frontendUrl + "/auth/delegate/error?reason=expired")
                    .build();
        } catch (Exception e) {
            log.warn("Invalid delegate token during authorize: {}", e.getMessage());
            return ResponseEntity.status(302)
                    .header("Location", frontendUrl + "/auth/delegate/error?reason=invalid")
                    .build();
        }
    }

    @GetMapping("/callback/google")
    public ResponseEntity<Void> googleCallback(@RequestParam("code") String code, @RequestParam("state") String state) {
        log.info("Google delegate OAuth callback");
        try {
            Claims claims = jwtService.validateDelegateToken(state);
            String delegateEmail = claims.getSubject();

            String authenticatedEmail = googleConfig.handleDelegateAuthorizationCode(code, delegateEmail);
            log.info("Google delegate token exchange success, authenticatedEmail={}", authenticatedEmail);

            if (authenticatedEmail != null && !authenticatedEmail.equalsIgnoreCase(delegateEmail)) {
                log.warn("Email mismatch: expected={}, got={}. Rejecting.", delegateEmail, authenticatedEmail);
                return ResponseEntity.status(302)
                        .header("Location", frontendUrl + "/auth/delegate/error?reason=email_mismatch")
                        .build();
            }

            delegateStore.persistDelegate(delegateEmail, claims.get("provider", String.class), claims.get("requester", String.class));
            log.info("Delegate authorized successfully: {}", delegateEmail);

            return ResponseEntity.status(302).header("Location", frontendUrl + "/auth/delegate/success").build();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.error("Delegate JWT expired during callback");
            return ResponseEntity.status(302).header("Location", frontendUrl + "/auth/delegate/error?reason=expired").build();
        } catch (Exception e) {
            log.error("Google delegate callback failed", e);
            return ResponseEntity.status(302).header("Location", frontendUrl + "/auth/delegate/error").build();
        }
    }

    @GetMapping("/callback/microsoft")
    public ResponseEntity<Void> microsoftCallback(@RequestParam("code") String code, @RequestParam("state") String state) {
        log.info("Microsoft delegate OAuth callback");
        try {
            Claims claims = jwtService.validateDelegateToken(state);
            String delegateEmail = claims.getSubject();

            microsoftConfig.handleDelegateAuthorizationCode(code, delegateEmail);
            log.info("Microsoft delegate token exchange success for: {}", delegateEmail);

            delegateStore.persistDelegate(delegateEmail, claims.get("provider", String.class), claims.get("requester", String.class));
            log.info("Delegate authorized successfully: {}", delegateEmail);

            return ResponseEntity.status(302).header("Location", frontendUrl + "/auth/delegate/success").build();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.error("Delegate JWT expired during callback");
            return ResponseEntity.status(302).header("Location", frontendUrl + "/auth/delegate/error?reason=expired").build();
        } catch (Exception e) {
            log.error("Microsoft delegate callback failed", e);
            return ResponseEntity.status(302).header("Location", frontendUrl + "/auth/delegate/error").build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listDelegates() {
        String requester = currentUserEmail();
        log.info("Listing authorized delegates for {}", requester);
        return ResponseEntity.ok(delegateStore.listAuthorizedDelegatesMapped(requester));
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Void> revokeDelegate(@PathVariable String email) throws Exception {
        String requester = currentUserEmail();
        log.info("Revoking delegate access: requester={}, delegate={}", requester, email);
        delegateStore.removeDelegate(requester, email);
        try {
            microsoftConfig.revokeDelegateAuthorization(email);
        } catch (Exception e) {
            log.debug("No Microsoft tokens to revoke for {}", email);
        }
        return ResponseEntity.noContent().build();
    }

    private String currentUserEmail() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }
}
