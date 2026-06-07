package com.smartscheduler.scheduler.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.smartscheduler.scheduler.model.DelegateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DelegateStore {

    private static final Logger log = LoggerFactory.getLogger(DelegateStore.class);
    private static final String DELEGATES_FILE = "delegates.json";
    private static final Type DELEGATE_LIST_TYPE = new TypeToken<List<DelegateRequest>>() {}.getType();

    private final ConcurrentHashMap<String, DelegateRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Value("${microsoft.oauth.tokens-directory:.oauth-tokens}")
    private String tokensDirectory;

    public DelegateRequest createRequest(String requesterEmail, String delegateEmail, String provider, long expiryHours) {
        log.info("Creating delegate request: requester={}, delegate={}, provider={}", requesterEmail, delegateEmail, provider);
        DelegateRequest request = new DelegateRequest();
        request.setId(UUID.randomUUID().toString());
        request.setRequesterEmail(requesterEmail);
        request.setDelegateEmail(delegateEmail);
        request.setProvider(provider);
        long now = System.currentTimeMillis();
        request.setCreatedAt(String.valueOf(now));
        request.setExpiresAt(String.valueOf(now + expiryHours * 3600 * 1000));
        request.setStatus("PENDING");
        pendingRequests.put(request.getId(), request);
        log.info("Delegate request created with id={}", request.getId());
        return request;
    }

    public DelegateRequest getRequest(String token) {
        return pendingRequests.get(token);
    }

    public void markAuthorized(String token) {
        log.info("Marking delegate request as authorized: token={}", token);
        DelegateRequest request = pendingRequests.get(token);
        if (request != null) {
            request.setStatus("AUTHORIZED");
            persistAuthorizedDelegate(request);
        }
    }

    public DelegateRequest findPendingByDelegateEmail(String delegateEmail) {
        return pendingRequests.values().stream()
                .filter(r -> r.getDelegateEmail().equalsIgnoreCase(delegateEmail) && "PENDING".equals(r.getStatus()))
                .findFirst()
                .orElse(null);
    }

    public List<DelegateRequest> listAuthorizedDelegates() {
        try {
            Path filePath = Path.of(tokensDirectory, DELEGATES_FILE);
            if (!Files.exists(filePath)) {
                return new ArrayList<>();
            }
            String json = Files.readString(filePath, StandardCharsets.UTF_8);
            List<DelegateRequest> delegates = gson.fromJson(json, DELEGATE_LIST_TYPE);
            return delegates != null ? delegates : new ArrayList<>();
        } catch (IOException e) {
            log.error("Failed to read delegates file", e);
            return new ArrayList<>();
        }
    }

    public void removeDelegate(String email) {
        log.info("Removing delegate: email={}", email);
        List<DelegateRequest> delegates = listAuthorizedDelegates();
        delegates.removeIf(d -> d.getDelegateEmail().equalsIgnoreCase(email));
        saveDelegates(delegates);
    }

    private void persistAuthorizedDelegate(DelegateRequest request) {
        log.info("Persisting authorized delegate: email={}", request.getDelegateEmail());
        List<DelegateRequest> delegates = listAuthorizedDelegates();
        delegates.removeIf(d -> d.getDelegateEmail().equalsIgnoreCase(request.getDelegateEmail()));
        delegates.add(request);
        saveDelegates(delegates);
    }

    private void saveDelegates(List<DelegateRequest> delegates) {
        try {
            Path dir = Path.of(tokensDirectory);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(DELEGATES_FILE), gson.toJson(delegates), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to save delegates file", e);
        }
    }
}
