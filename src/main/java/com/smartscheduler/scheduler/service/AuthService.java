package com.smartscheduler.scheduler.service;

import com.smartscheduler.scheduler.model.User;
import com.smartscheduler.scheduler.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User signupWithEmail(String email, String password, String name) {
        log.info("Signup with email: {}", email);
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Account already exists for " + email);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(name != null ? name : email.split("@")[0]);
        user.setCalendarConnected(false);
        user.setLoginMethod("email");
        user.setCreatedAt(String.valueOf(System.currentTimeMillis()));
        return userRepository.save(user);
    }

    public User loginWithEmail(String email, String password) {
        log.info("Login with email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("No account found for " + email));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalStateException("Invalid password");
        }
        return user;
    }

    public User findOrCreateOAuthUser(String email, String provider) {
        log.info("OAuth login/signup: email={}, provider={}", email, provider);
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setName(email.split("@")[0]);
            user.setCalendarProvider(provider);
            user.setCalendarConnected(true);
            user.setLoginMethod(provider);
            user.setCreatedAt(String.valueOf(System.currentTimeMillis()));
            return userRepository.save(user);
        });
    }

    public User connectCalendar(String email, String provider) {
        log.info("Connecting calendar for {}: provider={}", email, provider);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));
        user.setCalendarProvider(provider);
        user.setCalendarConnected(true);
        return userRepository.save(user);
    }

    public User disconnectCalendar(String email) {
        log.info("Disconnecting calendar for {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));
        user.setCalendarProvider(null);
        user.setCalendarConnected(false);
        return userRepository.save(user);
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}
