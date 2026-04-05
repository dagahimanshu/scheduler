package com.smartscheduler.scheduler.controller;

import com.smartscheduler.scheduler.configuration.GoogleCalendarConfig;
import com.smartscheduler.scheduler.model.TaskRequest;
import com.smartscheduler.scheduler.service.CalendarService;
import com.smartscheduler.scheduler.service.EventListingService;
import com.smartscheduler.scheduler.service.PriorityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;


import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SchedulerController {

    private final CalendarService calendarService;
    private final EventListingService eventListingService;
    private final GoogleCalendarConfig googleCalendarConfig;
    private final PriorityService priorityService;
    private final HttpServletResponse httpResponse;

    public SchedulerController(
            CalendarService calendarService,
            EventListingService eventListingService,
            GoogleCalendarConfig googleCalendarConfig,
            PriorityService priorityService, HttpServletResponse httpServletResponse
    ) {
        this.calendarService = calendarService;
        this.eventListingService = eventListingService;
        this.googleCalendarConfig = googleCalendarConfig;
        this.priorityService = priorityService;
        this.httpResponse = httpServletResponse;
    }

    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(@RequestBody TaskRequest task) {
        try {
            priorityService.populateMissingPriority(task);
            String link = calendarService.createEvent(task);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Event created");
            response.put("eventLink", link);
            response.put("priority", task.getPriority());
            response.put("description", task.getDescription());

            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Remove the old @PostMapping("/auth/google") method and replace with:
    @GetMapping("/auth/google/url")
    public ResponseEntity<?> getAuthUrl() {
        try {
            String url = googleCalendarConfig.buildAuthorizationUrl();
            Map<String, String> response = new LinkedHashMap<>();
            response.put("url", url);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to build auth URL: " + e.getMessage());
        }
    }

    @GetMapping("/auth/google/callback")
    public void handleCallback(@RequestParam String code) throws IOException {
        try {
            googleCalendarConfig.handleAuthorizationCode(code);
            // Redirect popup to frontend success page
            httpResponse.sendRedirect("http://localhost:3000/auth/success");
        } catch (Exception e) {
            httpResponse.sendRedirect("http://localhost:3000/auth/error");
        }
    }

    @GetMapping("/auth/google/status")
    public ResponseEntity<?> authStatus() {
        Map<String, Boolean> response = new LinkedHashMap<>();
        response.put("authorized", googleCalendarConfig.isAuthorized());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/events/next-day")
    public ResponseEntity<?> getNextDayEvents() {
        try {
            return ResponseEntity.ok(eventListingService.getEventsUntil24Hrs());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

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
}
