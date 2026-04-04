package com.smartscheduler.scheduler.controller;

import com.smartscheduler.scheduler.configuration.GoogleCalendarConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final GoogleCalendarConfig googleCalendarConfig;

    public AuthController(GoogleCalendarConfig googleCalendarConfig) {
        this.googleCalendarConfig = googleCalendarConfig;
    }

    @PostMapping("/google")
    public ResponseEntity<String> authorizeGoogle() {
        try {
            googleCalendarConfig.authorizeUser();
            return ResponseEntity.ok("Google Calendar authorization completed.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Authorization failed: " + e.getMessage());
        }
    }
}
