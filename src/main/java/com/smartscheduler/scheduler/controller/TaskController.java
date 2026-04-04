package com.smartscheduler.scheduler.controller;

import com.smartscheduler.scheduler.model.TaskRequest;
import com.smartscheduler.scheduler.service.CalendarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final CalendarService calendarService;

    public TaskController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @PostMapping
    public ResponseEntity<?> createTask(@RequestBody TaskRequest task) {
        try {
            String link = calendarService.createEvent(task);
            return ResponseEntity.ok("Event created: " + link);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
