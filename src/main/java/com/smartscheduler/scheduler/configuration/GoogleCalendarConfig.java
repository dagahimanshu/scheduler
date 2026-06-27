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

import java.io.File;
import java.util.List;

@Configuration
public class GoogleCalendarConfig {

    private GoogleClientSecrets cachedClientSecrets;

    private static final String APPLICATION_NAME = "Task Scheduler";
    private static final List<String> SCOPES = List.of(CalendarScopes.CALENDAR, "email", "profile");
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${google.oauth.client-id:}")
    private String clientId;

    @Value("${google.oauth.client-secret:}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${google.oauth.tokens-directory:.oauth-tokens}")
    private String tokensDirectory;

    @Value("${app.backend-url}")
    private String backendUrl;

    public String buildAuthorizationUrl() throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setAccessType("offline")
                .set("prompt", "consent")
                .build();
    }

    public String handleAuthorizationCode(String code) throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow();

        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                clientId,
                clientSecret,
                code,
                redirectUri
        ).execute();

        String email = null;
        String idTokenStr = tokenResponse.getIdToken();
        if (idTokenStr != null) {
            com.google.api.client.googleapis.auth.oauth2.GoogleIdToken idToken =
                    com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.parse(JSON_FACTORY, idTokenStr);
            if (idToken != null && idToken.getPayload() != null) {
                email = idToken.getPayload().getEmail();
            }
        }
        String credentialKey = email != null ? email : "user";
        flow.createAndStoreCredential(tokenResponse, credentialKey);
        return email;
    }

    public Calendar getCalendarService(String userEmail) throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        Credential credential = flow.loadCredential(userEmail);

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

    public boolean isAuthorized(String userEmail) {
        try {
            GoogleAuthorizationCodeFlow flow = buildFlow();
            Credential credential = flow.loadCredential(userEmail);
            return credential != null && credential.getRefreshToken() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void revokeAuthorization() throws Exception {
        File tokenDir = new File(tokensDirectory);
        if (tokenDir.exists()) {
            File[] files = tokenDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().startsWith("StoredCredential")) {
                        f.delete();
                    }
                }
            }
        }
    }

    private GoogleAuthorizationCodeFlow buildFlow() throws Exception {
        if (cachedClientSecrets == null) {
            cachedClientSecrets = buildClientSecrets();
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

    private GoogleClientSecrets buildClientSecrets() {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "Set google.oauth.client-id in application-local.properties");
        }
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(clientId);
        details.setClientSecret(clientSecret);
        details.setAuthUri("https://accounts.google.com/o/oauth2/auth");
        details.setTokenUri("https://oauth2.googleapis.com/token");

        GoogleClientSecrets secrets = new GoogleClientSecrets();
        secrets.setInstalled(details);
        return secrets;
    }

    public String buildDelegateAuthorizationUrl(String delegateEmail) throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        return flow.newAuthorizationUrl()
                .setRedirectUri(backendUrl + "/delegates/callback/google")
                .setAccessType("offline")
                .set("prompt", "consent")
                .setState(delegateEmail)
                .build();
    }

    public void handleDelegateAuthorizationCode(String code, String delegateEmail) throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                clientId,
                clientSecret,
                code,
                backendUrl + "/delegates/callback/google"
        ).execute();
        flow.createAndStoreCredential(tokenResponse, delegateEmail);
    }

    public Calendar getCalendarServiceForDelegate(String delegateEmail) throws Exception {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        Credential credential = flow.loadCredential(delegateEmail);
        if (credential == null) {
            throw new IllegalStateException("No delegate authorization found for " + delegateEmail);
        }
        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
