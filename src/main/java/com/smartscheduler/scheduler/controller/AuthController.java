package com.smartscheduler.scheduler.controller;

import com.smartscheduler.scheduler.model.User;
import com.smartscheduler.scheduler.service.AuthService;
import com.smartscheduler.scheduler.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            String password = body.get("password");
            String name = body.get("name");
            log.info("Signup request for email: {}", email);
            if (email == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
            }
            if (password.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
            }
            User user = authService.signupWithEmail(email, password, name);
            String token = jwtService.generateToken(user.getEmail(), null);
            log.info("Signup successful for email: {}", email);
            return ResponseEntity.ok(buildAuthResponse(user, token));
        } catch (IllegalStateException e) {
            log.warn("Signup failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            String password = body.get("password");
            String provider = body.get("provider");
            log.info("Login request: email={}, provider={}", email, provider);

            if (password == null && provider != null) {
                User user = authService.findOrCreateOAuthUser(email, provider);
                String token = jwtService.generateToken(user.getEmail(), user.getCalendarProvider());
                return ResponseEntity.ok(buildAuthResponse(user, token));
            }

            if (email == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
            }
            User user = authService.loginWithEmail(email, password);
            String token = jwtService.generateToken(user.getEmail(), user.getCalendarProvider());
            log.info("Login successful for email: {}", email);
            return ResponseEntity.ok(buildAuthResponse(user, token));
        } catch (IllegalStateException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No token provided"));
        }
        try {
            String token = authHeader.substring(7);
            String email = jwtService.getEmailFromToken(token);
            log.info("GET /auth/me for email: {}", email);
            if (email == null || email.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token — no email"));
            }
            User user = authService.getUserByEmail(email);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
            }
            return ResponseEntity.ok(buildUserResponse(user));
        } catch (Exception e) {
            log.warn("GET /auth/me failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token"));
        }
    }

    @PostMapping("/connect-calendar")
    public ResponseEntity<?> connectCalendar(@RequestBody Map<String, String> body,
                                              @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtService.getEmailFromToken(token);
            String provider = body.get("provider");
            log.info("Connect calendar: email={}, provider={}", email, provider);
            User user = authService.connectCalendar(email, provider);
            String newToken = jwtService.generateToken(user.getEmail(), user.getCalendarProvider());
            return ResponseEntity.ok(buildAuthResponse(user, newToken));
        } catch (Exception e) {
            log.error("Connect calendar failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> buildAuthResponse(User user, String token) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.putAll(buildUserResponse(user));
        return response;
    }

    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("email", user.getEmail());
        response.put("name", user.getName());
        response.put("calendarProvider", user.getCalendarProvider());
        response.put("calendarConnected", user.isCalendarConnected());
        response.put("loginMethod", user.getLoginMethod());
        return response;
    }
}
