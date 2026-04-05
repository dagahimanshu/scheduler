package com.smartscheduler.scheduler.configuration;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

@Configuration
public class GoogleCalendarConfig {

    private GoogleClientSecrets cachedClientSecrets;

    private static final String APPLICATION_NAME = "Task Scheduler";
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    // This must match exactly what's registered in Google Cloud Console
    // e.g. http://localhost:9090/auth/google/callback
    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${google.oauth.tokens-directory:.oauth-tokens}")
    private String tokensDirectory;

    @Value("${google.oauth.credentials-path:}")
    private String credentialsPath;

    public String buildAuthorizationUrl() throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setAccessType("offline")
                // force consent screen so refresh_token is always returned
                .set("prompt", "consent")
                .build();
    }

    public void handleAuthorizationCode(String code) throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow(); // populates cachedClientSecrets

        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                cachedClientSecrets.getDetails().getClientId(),     // ← correct
                cachedClientSecrets.getDetails().getClientSecret(), // ← correct
                code,
                redirectUri
        ).execute();

        flow.createAndStoreCredential(tokenResponse, "user");
    }

        // ── Used by CalendarService (unchanged behaviour) ─────────────────────────
    public Calendar getCalendarService() throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        Credential credential = flow.loadCredential("user");

        if (credential == null) {
            throw new IllegalStateException(
                    "Google Calendar not authorized. Complete OAuth flow first."
            );
        }

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // ── Check if already authorized (UI can poll this) ────────────────────────
    public boolean isAuthorized() {
        try {
            GoogleAuthorizationCodeFlow flow = buildFlow();
            Credential credential = flow.loadCredential("user");
            return credential != null && credential.getRefreshToken() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private GoogleAuthorizationCodeFlow buildFlow() throws Exception {
        if (cachedClientSecrets == null) {
            cachedClientSecrets = loadClientSecrets();
        }
        File tokenStoreDir = new File(tokensDirectory);

        return new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                cachedClientSecrets,
                SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokenStoreDir))
                .setAccessType("offline")
                .build();
    }

    private GoogleClientSecrets loadClientSecrets() throws Exception {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException(
                    "Set google.oauth.credentials-path in application-local.properties"
            );
        }
        try (InputStream in = openCredentialsStream()) {
            return GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        }
    }

    private InputStream openCredentialsStream() throws Exception {
        File file = new File(credentialsPath);
        if (file.exists()) return new FileInputStream(file);

        ClassPathResource cp = new ClassPathResource(credentialsPath);
        if (cp.exists()) return cp.getInputStream();

        throw new IllegalStateException("Credentials file not found: " + credentialsPath);
    }
}