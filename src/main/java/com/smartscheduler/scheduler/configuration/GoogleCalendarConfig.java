package com.smartscheduler.scheduler.configuration;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Collections;
import java.util.List;

@Configuration
public class GoogleCalendarConfig {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarConfig.class);
    private static final String APPLICATION_NAME = "Task Scheduler";
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${google.oauth.callback-host:localhost}")
    private String callbackHost;

    @Value("${google.oauth.callback-port:8888}")
    private int callbackPort;

    @Value("${google.oauth.callback-path:/oauth2/callback}")
    private String callbackPath;

    @Value("${google.oauth.tokens-directory:.oauth-tokens}")
    private String tokensDirectory;

    @Value("${google.oauth.credentials-path:}")
    private String credentialsPath;

    public void authorizeUser() throws Exception {
        GoogleClientSecrets clientSecrets = loadClientSecrets();

        File tokenStoreDir = new File(tokensDirectory);

        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        JSON_FACTORY,
                        clientSecrets,
                        SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(tokenStoreDir))
                        .setAccessType("offline")
                        .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setHost(callbackHost)
                .setPort(callbackPort)
                .setCallbackPath(callbackPath)
                .build();

        new AuthorizationCodeInstalledApp(flow, receiver, this::openInBrowser)
                .authorize("user");
    }

    public Calendar getCalendarService() throws Exception {
        GoogleClientSecrets clientSecrets = loadClientSecrets();

        File tokenStoreDir = new File(tokensDirectory);

        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        JSON_FACTORY,
                        clientSecrets,
                        SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(tokenStoreDir))
                        .setAccessType("offline")
                        .build();

        Credential credential = flow.loadCredential("user");
        if (credential == null) {
            throw new IllegalStateException("Google Calendar is not authorized yet. Call /auth/google first.");
        }

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private GoogleClientSecrets loadClientSecrets() throws Exception {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException(
                    "Google OAuth credentials are missing. Set google.oauth.credentials-path in application-local.properties."
            );
        }

        try (InputStream in = openCredentialsStream()) {
            return GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        }
    }

    private InputStream openCredentialsStream() throws Exception {
        File credentialsFile = new File(credentialsPath);
        if (credentialsFile.exists()) {
            return new FileInputStream(credentialsFile);
        }

        ClassPathResource classPathResource = new ClassPathResource(credentialsPath);
        if (classPathResource.exists()) {
            return classPathResource.getInputStream();
        }

        throw new IllegalStateException(
                "Google OAuth credentials file not found: " + credentialsPath
        );
    }

    private void openInBrowser(String url) {
        logger.info("Opening Google authorization page in your browser.");
        logger.info("If the browser does not open, use this URL manually: {}", url);

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ex) {
            logger.debug("Desktop browser launch failed, falling back to OS-specific command.", ex);
        }

        try {
            String osName = System.getProperty("os.name", "").toLowerCase();

            if (osName.contains("mac")) {
                new ProcessBuilder("open", url).start();
                return;
            }

            if (osName.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                return;
            }

            if (osName.contains("nix") || osName.contains("nux")) {
                new ProcessBuilder("xdg-open", url).start();
                return;
            }
        } catch (Exception ex) {
            logger.warn("Unable to open the browser automatically. Open the authorization URL manually.", ex);
        }
    }
}
