package com.smartscheduler.scheduler.controller;

import com.smartscheduler.scheduler.configuration.GoogleCalendarConfig;
import com.smartscheduler.scheduler.configuration.MicrosoftCalendarConfig;
import com.smartscheduler.scheduler.model.TaskRequest;
import com.smartscheduler.scheduler.service.CalendarService;
import com.smartscheduler.scheduler.service.EventListingService;
import com.smartscheduler.scheduler.service.MicrosoftCalendarService;
import com.smartscheduler.scheduler.service.MicrosoftEventListingService;
import com.smartscheduler.scheduler.service.PriorityService;
import com.smartscheduler.scheduler.service.ProviderDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SchedulerController {

    private static final Logger log = LoggerFactory.getLogger(SchedulerController.class);

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final CalendarService calendarService;
    private final EventListingService eventListingService;
    private final GoogleCalendarConfig googleCalendarConfig;
    private final MicrosoftCalendarConfig microsoftCalendarConfig;
    private final MicrosoftCalendarService microsoftCalendarService;
    private final MicrosoftEventListingService microsoftEventListingService;
    private final PriorityService priorityService;
    private final ProviderDetectionService providerDetectionService;
    private final HttpServletResponse httpResponse;

    public SchedulerController(
            CalendarService calendarService,
            EventListingService eventListingService,
            GoogleCalendarConfig googleCalendarConfig,
            MicrosoftCalendarConfig microsoftCalendarConfig,
            MicrosoftCalendarService microsoftCalendarService,
            MicrosoftEventListingService microsoftEventListingService,
            PriorityService priorityService,
            ProviderDetectionService providerDetectionService,
            HttpServletResponse httpServletResponse
    ) {
        this.calendarService = calendarService;
        this.eventListingService = eventListingService;
        this.googleCalendarConfig = googleCalendarConfig;
        this.microsoftCalendarConfig = microsoftCalendarConfig;
        this.microsoftCalendarService = microsoftCalendarService;
        this.microsoftEventListingService = microsoftEventListingService;
        this.priorityService = priorityService;
        this.providerDetectionService = providerDetectionService;
        this.httpResponse = httpServletResponse;
    }

    // ── Provider detection via MX lookup ───────────────────────────────────────

    @GetMapping("/auth/detect-provider")
    public ResponseEntity<?> detectProvider(@RequestParam String email) {
        log.info("detect-provider called with email: {}", email);
        try {
            String provider = providerDetectionService.detectProvider(email);
            log.info("detect-provider result for {}: provider={}", email, provider);
            if (provider == null) {
                log.warn("detect-provider: unknown provider for {}", email);
                return ResponseEntity.ok(Map.of(
                        "provider", "unknown",
                        "message", "Could not determine calendar provider for this email domain"
                ));
            }
            boolean authorized = "microsoft".equals(provider)
                    ? microsoftCalendarConfig.isAuthorized()
                    : googleCalendarConfig.isAuthorized();
            log.info("detect-provider: provider={}, authorized={}", provider, authorized);
            return ResponseEntity.ok(Map.of(
                    "provider", provider,
                    "email", email,
                    "authorized", authorized
            ));
        } catch (IllegalArgumentException e) {
            log.error("detect-provider bad request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("detect-provider failed for {}", email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Provider detection failed: " + e.getMessage()));
        }
    }

    // ── Task creation (routes to detected provider) ───────────────────────────

    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(@RequestBody TaskRequest task,
                                         @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            priorityService.populateMissingPriority(task);

            String link;
            if (isMicrosoft(task.getProvider())) {
                link = microsoftCalendarService.createEvent(task, timezone);
            } else {
                link = calendarService.createEvent(task, timezone);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Event created");
            response.put("eventLink", link);
            response.put("priority", task.getPriority());
            response.put("description", task.getDescription());
            response.put("provider", isMicrosoft(task.getProvider()) ? "microsoft" : "google");

            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ── Event listing ─────────────────────────────────────────────────────────

    @GetMapping("/events/next-day")
    public ResponseEntity<?> getNextDayEvents(
            @RequestParam(value = "provider", defaultValue = "google") String provider,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            if (isMicrosoft(provider)) {
                return ResponseEntity.ok(microsoftEventListingService.getEventsUntil24Hrs(timezone));
            }
            return ResponseEntity.ok(eventListingService.getEventsUntil24Hrs(timezone));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ── Week event listing ─────────────────────────────────────────────────────

    @GetMapping("/events/week")
    public ResponseEntity<?> getWeekEvents(
            @RequestParam(value = "provider", defaultValue = "google") String provider,
            @RequestParam String startDate,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        log.info("events/week called: provider={}, startDate={}, tz={}", provider, startDate, timezone);
        try {
            if (isMicrosoft(provider)) {
                return ResponseEntity.ok(microsoftEventListingService.getEventsForWeek(startDate, timezone));
            }
            return ResponseEntity.ok(eventListingService.getEventsForWeek(startDate, timezone));
        } catch (IllegalStateException e) {
            log.error("events/week unauthorized: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            log.error("events/week failed for provider={}, startDate={}", provider, startDate, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ── Event time update (drag-and-drop) ────────────────────────────────────

    @PatchMapping("/events/{eventId}")
    public ResponseEntity<?> updateEventTime(
            @PathVariable String eventId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        log.info("PATCH /events/{} called with body: {}", eventId, body);
        try {
            String start = body.get("start");
            String end = body.get("end");
            String provider = body.getOrDefault("provider", "google");

            if (start == null || end == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "start and end are required"));
            }

            if (isMicrosoft(provider)) {
                String timeZone = timezone != null ? timezone : body.getOrDefault("timeZone", "Asia/Kolkata");
                microsoftCalendarService.updateEventTime(eventId, start, end, timeZone);
            } else {
                calendarService.updateEventTime(eventId, start, end, timezone);
            }

            log.info("Event {} updated successfully via {}", eventId, provider);
            return ResponseEntity.ok(Map.of("message", "Event updated", "eventId", eventId, "provider", provider));
        } catch (IllegalStateException e) {
            log.error("PATCH /events/{} unauthorized: {}", eventId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            log.error("PATCH /events/{} failed", eventId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ── Google OAuth ──────────────────────────────────────────────────────────

    @GetMapping("/auth/google/url")
    public ResponseEntity<?> getGoogleAuthUrl() {
        log.info("auth/google/url called");
        try {
            String url = googleCalendarConfig.buildAuthorizationUrl();
            log.info("auth/google/url built successfully");
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("auth/google/url failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to build auth URL: " + e.getMessage());
        }
    }

    @GetMapping("/auth/google/callback")
    public void handleGoogleCallback(@RequestParam String code) throws IOException {
        log.info("auth/google/callback received code: {}...", code.substring(0, Math.min(10, code.length())));
        try {
            String email = googleCalendarConfig.handleAuthorizationCode(code);
            log.info("auth/google/callback success, email={}", email);
            String redirect = frontendUrl + "/auth/success?provider=google";
            if (email != null) redirect += "&email=" + java.net.URLEncoder.encode(email, "UTF-8");
            httpResponse.sendRedirect(redirect);
        } catch (Exception e) {
            log.error("auth/google/callback failed", e);
            httpResponse.sendRedirect(frontendUrl + "/auth/error?provider=google");
        }
    }

    @GetMapping("/auth/google/status")
    public ResponseEntity<?> googleAuthStatus() {
        return ResponseEntity.ok(Map.of("authorized", googleCalendarConfig.isAuthorized()));
    }

    // ── Microsoft OAuth ───────────────────────────────────────────────────────

    @GetMapping("/auth/microsoft/url")
    public ResponseEntity<?> getMicrosoftAuthUrl() {
        log.info("auth/microsoft/url called");
        try {
            String url = microsoftCalendarConfig.buildAuthorizationUrl();
            log.info("auth/microsoft/url built successfully");
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("auth/microsoft/url failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to build Microsoft auth URL: " + e.getMessage());
        }
    }

    @GetMapping("/auth/microsoft/callback")
    public void handleMicrosoftCallback(@RequestParam String code) throws IOException {
        log.info("auth/microsoft/callback received code: {}...", code.substring(0, Math.min(10, code.length())));
        try {
            microsoftCalendarConfig.handleAuthorizationCode(code);
            log.info("auth/microsoft/callback success — redirecting to /auth/success");
            httpResponse.sendRedirect(frontendUrl + "/auth/success?provider=microsoft");
        } catch (Exception e) {
            log.error("auth/microsoft/callback failed", e);
            httpResponse.sendRedirect(frontendUrl + "/auth/error?provider=microsoft");
        }
    }

    @GetMapping("/auth/microsoft/status")
    public ResponseEntity<?> microsoftAuthStatus() {
        return ResponseEntity.ok(Map.of("authorized", microsoftCalendarConfig.isAuthorized()));
    }

    // ── Combined auth status ──────────────────────────────────────────────────

    @GetMapping("/auth/status")
    public ResponseEntity<?> combinedAuthStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("google", Map.of("authorized", googleCalendarConfig.isAuthorized()));
        status.put("microsoft", Map.of("authorized", microsoftCalendarConfig.isAuthorized()));
        return ResponseEntity.ok(status);
    }

    // ── Disconnect / revoke ───────────────────────────────────────────────────

    @DeleteMapping("/auth/{provider}/disconnect")
    public ResponseEntity<?> disconnect(@PathVariable String provider) {
        log.info("disconnect called for provider: {}", provider);
        try {
            if (isMicrosoft(provider)) {
                microsoftCalendarConfig.revokeAuthorization();
            } else {
                googleCalendarConfig.revokeAuthorization();
            }
            log.info("disconnect success for {}", provider);
            return ResponseEntity.ok(Map.of("disconnected", true, "provider", provider));
        } catch (Exception e) {
            log.error("disconnect failed for {}", provider, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to disconnect: " + e.getMessage()));
        }
    }

    // ── Priority test ─────────────────────────────────────────────────────────

    @PostMapping("/tasks/priority/test")
    public ResponseEntity<?> testPriority(@RequestBody TaskRequest task) {
        try {
            String rawResponse = priorityService.inferPriority(task);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("task", task);
            response.put("submittedPriority", task == null ? null : task.getPriority());
            response.put("inferredPriority", priorityService.extractTopPriority(rawResponse));
            response.put("huggingFaceResponse", rawResponse);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Priority test failed: " + e.getMessage());
        }
    }

    private boolean isMicrosoft(String provider) {
        return "microsoft".equalsIgnoreCase(provider);
    }
}
