package com.smartscheduler.scheduler.configuration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.StringJoiner;

@Configuration
public class MicrosoftCalendarConfig {

    private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String SCOPES = "openid profile email offline_access Calendars.ReadWrite";

    @Value("${microsoft.oauth.client-id:}")
    private String clientId;

    @Value("${microsoft.oauth.client-secret:}")
    private String clientSecret;

    @Value("${microsoft.oauth.tenant-id:common}")
    private String tenantId;

    @Value("${microsoft.oauth.redirect-uri:http://localhost:9090/auth/microsoft/callback}")
    private String redirectUri;

    @Value("${microsoft.oauth.tokens-directory:.oauth-tokens}")
    private String tokensDirectory;

    private static final String TOKEN_FILE = "microsoft-tokens.json";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public String buildAuthorizationUrl() {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "Set microsoft.oauth.client-id in application-local.properties");
        }

        String authEndpoint = String.format(AUTHORIZE_URL, tenantId);

        return authEndpoint
                + "?client_id=" + enc(clientId)
                + "&response_type=code"
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_mode=query"
                + "&scope=" + enc(SCOPES)
                + "&prompt=select_account";
    }

    public void handleAuthorizationCode(String code) throws Exception {
        String tokenEndpoint = String.format(TOKEN_URL, tenantId);

        StringJoiner body = new StringJoiner("&");
        body.add("client_id=" + enc(clientId));
        body.add("client_secret=" + enc(clientSecret));
        body.add("code=" + enc(code));
        body.add("redirect_uri=" + enc(redirectUri));
        body.add("grant_type=authorization_code");
        body.add("scope=" + enc(SCOPES));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Token exchange failed: " + response.body());
        }

        storeTokens(response.body());
    }

    public String getAccessToken() throws Exception {
        JsonObject tokens = loadTokens();
        if (tokens == null) {
            throw new IllegalStateException(
                    "Microsoft Calendar not authorized. Complete OAuth flow first.");
        }

        long expiresAt = tokens.has("expires_at") ? tokens.get("expires_at").getAsLong() : 0;
        if (System.currentTimeMillis() > expiresAt - 60_000) {
            tokens = refreshAccessToken(tokens);
        }

        return tokens.get("access_token").getAsString();
    }

    public boolean isAuthorized() {
        try {
            JsonObject tokens = loadTokens();
            return tokens != null && tokens.has("refresh_token");
        } catch (Exception e) {
            return false;
        }
    }

    public void revokeAuthorization() throws IOException {
        Path tokenPath = Path.of(tokensDirectory, TOKEN_FILE);
        Files.deleteIfExists(tokenPath);
    }

    private JsonObject refreshAccessToken(JsonObject tokens) throws Exception {
        String refreshToken = tokens.get("refresh_token").getAsString();
        String tokenEndpoint = String.format(TOKEN_URL, tenantId);

        StringJoiner body = new StringJoiner("&");
        body.add("client_id=" + enc(clientId));
        body.add("client_secret=" + enc(clientSecret));
        body.add("refresh_token=" + enc(refreshToken));
        body.add("grant_type=refresh_token");
        body.add("scope=" + enc(SCOPES));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Token refresh failed: " + response.body());
        }

        JsonObject refreshed = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!refreshed.has("refresh_token")) {
            refreshed.addProperty("refresh_token", refreshToken);
        }

        storeTokens(gson.toJson(refreshed));
        return loadTokens();
    }

    private void storeTokens(String tokenJson) throws IOException {
        JsonObject tokens = JsonParser.parseString(tokenJson).getAsJsonObject();

        if (tokens.has("expires_in")) {
            long expiresAt = System.currentTimeMillis() + tokens.get("expires_in").getAsLong() * 1000;
            tokens.addProperty("expires_at", expiresAt);
        }

        Path dir = Path.of(tokensDirectory);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(TOKEN_FILE), gson.toJson(tokens), StandardCharsets.UTF_8);
    }

    private JsonObject loadTokens() throws IOException {
        Path tokenPath = Path.of(tokensDirectory, TOKEN_FILE);
        if (!Files.exists(tokenPath)) {
            return null;
        }
        String json = Files.readString(tokenPath, StandardCharsets.UTF_8);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String buildDelegateAuthorizationUrl(String delegateEmail) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Set microsoft.oauth.client-id");
        }
        String authEndpoint = String.format(AUTHORIZE_URL, tenantId);
        return authEndpoint
                + "?client_id=" + enc(clientId)
                + "&response_type=code"
                + "&redirect_uri=" + enc("http://localhost:9090/delegates/callback/microsoft")
                + "&response_mode=query"
                + "&scope=" + enc(SCOPES)
                + "&prompt=select_account"
                + "&state=" + enc(delegateEmail);
    }

    public void handleDelegateAuthorizationCode(String code, String delegateEmail) throws Exception {
        String tokenEndpoint = String.format(TOKEN_URL, tenantId);
        StringJoiner body = new StringJoiner("&");
        body.add("client_id=" + enc(clientId));
        body.add("client_secret=" + enc(clientSecret));
        body.add("code=" + enc(code));
        body.add("redirect_uri=" + enc("http://localhost:9090/delegates/callback/microsoft"));
        body.add("grant_type=authorization_code");
        body.add("scope=" + enc(SCOPES));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Delegate token exchange failed: " + response.body());
        }
        storeDelegateTokens(delegateEmail, response.body());
    }

    public String getAccessTokenForDelegate(String delegateEmail) throws Exception {
        JsonObject tokens = loadDelegateTokens(delegateEmail);
        if (tokens == null) {
            throw new IllegalStateException("No delegate authorization found for " + delegateEmail);
        }
        long expiresAt = tokens.has("expires_at") ? tokens.get("expires_at").getAsLong() : 0;
        if (System.currentTimeMillis() > expiresAt - 60_000) {
            tokens = refreshDelegateAccessToken(tokens, delegateEmail);
        }
        return tokens.get("access_token").getAsString();
    }

    public void revokeDelegateAuthorization(String delegateEmail) throws IOException {
        Path tokenPath = Path.of(tokensDirectory, "microsoft-delegate-" + delegateEmail.hashCode() + ".json");
        Files.deleteIfExists(tokenPath);
    }

    private void storeDelegateTokens(String delegateEmail, String tokenJson) throws IOException {
        JsonObject tokens = JsonParser.parseString(tokenJson).getAsJsonObject();
        if (tokens.has("expires_in")) {
            long expiresAt = System.currentTimeMillis() + tokens.get("expires_in").getAsLong() * 1000;
            tokens.addProperty("expires_at", expiresAt);
        }
        Path dir = Path.of(tokensDirectory);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("microsoft-delegate-" + delegateEmail.hashCode() + ".json"),
                gson.toJson(tokens), StandardCharsets.UTF_8);
    }

    private JsonObject loadDelegateTokens(String delegateEmail) throws IOException {
        Path tokenPath = Path.of(tokensDirectory, "microsoft-delegate-" + delegateEmail.hashCode() + ".json");
        if (!Files.exists(tokenPath)) return null;
        String json = Files.readString(tokenPath, StandardCharsets.UTF_8);
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private JsonObject refreshDelegateAccessToken(JsonObject tokens, String delegateEmail) throws Exception {
        String refreshToken = tokens.get("refresh_token").getAsString();
        String tokenEndpoint = String.format(TOKEN_URL, tenantId);
        StringJoiner body = new StringJoiner("&");
        body.add("client_id=" + enc(clientId));
        body.add("client_secret=" + enc(clientSecret));
        body.add("refresh_token=" + enc(refreshToken));
        body.add("grant_type=refresh_token");
        body.add("scope=" + enc(SCOPES));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Delegate token refresh failed: " + response.body());
        }
        JsonObject refreshed = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!refreshed.has("refresh_token")) {
            refreshed.addProperty("refresh_token", refreshToken);
        }
        storeDelegateTokens(delegateEmail, gson.toJson(refreshed));
        return loadDelegateTokens(delegateEmail);
    }
}
