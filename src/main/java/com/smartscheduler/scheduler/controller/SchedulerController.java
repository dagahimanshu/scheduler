package com.smartscheduler.scheduler.controller;

import com.smartscheduler.scheduler.configuration.GoogleCalendarConfig;
import com.smartscheduler.scheduler.model.TaskRequest;
import com.smartscheduler.scheduler.service.CalendarService;
import com.smartscheduler.scheduler.service.EventListingService;
import com.smartscheduler.scheduler.service.PriorityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SchedulerController {

    private final CalendarService calendarService;
    private final EventListingService eventListingService;
    private final GoogleCalendarConfig googleCalendarConfig;
    private final PriorityService priorityService;

    public SchedulerController(
            CalendarService calendarService,
            EventListingService eventListingService,
            GoogleCalendarConfig googleCalendarConfig,
            PriorityService priorityService
    ) {
        this.calendarService = calendarService;
        this.eventListingService = eventListingService;
        this.googleCalendarConfig = googleCalendarConfig;
        this.priorityService = priorityService;
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

    @PostMapping("/auth/google")
    public ResponseEntity<String> authorizeGoogle() {
        try {
            googleCalendarConfig.authorizeUser();
            return ResponseEntity.ok("Google Calendar authorization completed.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Authorization failed: " + e.getMessage());
        }
    }

    @GetMapping("/events/next-day")
    public ResponseEntity<?> getNextDayEvents() {
        try {
            return ResponseEntity.ok(eventListingService.getNextDayEvents());
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
