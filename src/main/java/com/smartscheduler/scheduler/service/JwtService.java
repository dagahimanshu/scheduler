package com.smartscheduler.scheduler.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration-hours:24}")
    private long expirationHours;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            byte[] randomKey = new byte[32];
            new SecureRandom().nextBytes(randomKey);
            secret = Base64.getEncoder().encodeToString(randomKey);
            log.warn("jwt.secret is not set — using auto-generated key. Tokens will not survive restarts.");
        }
        signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    public String generateToken(String email, String provider) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("provider", provider)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getEmailFromToken(String token) {
        return validateToken(token).getSubject();
    }

    public String generateDelegateToken(String requesterEmail, String delegateEmail, String provider) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(delegateEmail)
                .claim("requester", requesterEmail)
                .claim("provider", provider)
                .claim("purpose", "delegate-magic-auth")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(48, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
    }

    public Claims validateDelegateToken(String token) {
        Claims claims = validateToken(token);
        if (!"delegate-magic-auth".equals(claims.get("purpose", String.class))) {
            throw new io.jsonwebtoken.JwtException("Not a delegate magic-auth token");
        }
        return claims;
    }
}
