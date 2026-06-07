package com.smartscheduler.scheduler.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartscheduler.scheduler.configuration.MicrosoftCalendarConfig;
import com.smartscheduler.scheduler.model.CalendarEventResponse;
import com.smartscheduler.scheduler.model.TaskPriority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class MicrosoftEventListingService {

    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";

    private final MicrosoftCalendarConfig config;
    private final MicrosoftCalendarService microsoftCalendarService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${google.calendar.time-zone:Asia/Kolkata}")
    private String calendarTimeZone;

    public MicrosoftEventListingService(MicrosoftCalendarConfig config,
                                         MicrosoftCalendarService microsoftCalendarService) {
        this.config = config;
        this.microsoftCalendarService = microsoftCalendarService;
    }

    public List<CalendarEventResponse> getEventsUntil24Hrs() throws Exception {
        String accessToken = config.getAccessToken();
        ZoneId zoneId = ZoneId.of(calendarTimeZone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime end = now.plusDays(1);

        String startIso = now.toInstant().toString();
        String endIso = end.toInstant().toString();

        String url = GRAPH_BASE + "/me/calendarView"
                + "?startDateTime=" + enc(startIso)
                + "&endDateTime=" + enc(endIso)
                + "&$orderby=" + enc("start/dateTime")
                + "&$top=50"
                + "&$select=" + enc("id,subject,bodyPreview,start,end,location,importance,categories,webLink,isCancelled");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed to list Outlook events: " + response.body());
        }

        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray items = data.has("value") ? data.getAsJsonArray("value") : new JsonArray();

        List<CalendarEventResponse> results = new ArrayList<>();
        for (JsonElement el : items) {
            results.add(toResponse(el.getAsJsonObject()));
        }
        return results;
    }

    public List<CalendarEventResponse> getEventsForWeek(String startDate) throws Exception {
        String accessToken = config.getAccessToken();
        ZoneId zoneId = ZoneId.of(calendarTimeZone);
        ZonedDateTime weekStart = java.time.LocalDate.parse(startDate).atStartOfDay(zoneId);
        ZonedDateTime weekEnd = weekStart.plusDays(7);

        String startIso = weekStart.toInstant().toString();
        String endIso = weekEnd.toInstant().toString();

        String url = GRAPH_BASE + "/me/calendarView"
                + "?startDateTime=" + enc(startIso)
                + "&endDateTime=" + enc(endIso)
                + "&$orderby=" + enc("start/dateTime")
                + "&$top=250"
                + "&$select=" + enc("id,subject,bodyPreview,start,end,location,importance,categories,webLink,isCancelled");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed to list Outlook events: " + response.body());
        }

        JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray items = data.has("value") ? data.getAsJsonArray("value") : new JsonArray();

        List<CalendarEventResponse> results = new ArrayList<>();
        for (JsonElement el : items) {
            results.add(toResponse(el.getAsJsonObject()));
        }
        return results;
    }

    private CalendarEventResponse toResponse(JsonObject event) {
        String id = getStr(event, "id");
        String summary = getStr(event, "subject");
        String description = getStr(event, "bodyPreview");
        String importance = getStr(event, "importance");
        String webLink = getStr(event, "webLink");
        boolean cancelled = event.has("isCancelled") && event.get("isCancelled").getAsBoolean();
        String status = cancelled ? "cancelled" : "confirmed";

        String location = null;
        if (event.has("location") && event.get("location").isJsonObject()) {
            location = getStr(event.getAsJsonObject("location"), "displayName");
        }

        String priority = resolvePriority(event, importance);
        String colorId = microsoftCalendarService.resolveColorId(null,
                priority != null ? TaskPriority.valueOf(priority) : null);

        String start = extractDateTime(event, "start");
        String end = extractDateTime(event, "end");

        return new CalendarEventResponse(
                id, summary, description, colorId, priority,
                location, status, start, end, webLink
        );
    }

    private String resolvePriority(JsonObject event, String importance) {
        if (event.has("categories") && event.get("categories").isJsonArray()) {
            JsonArray cats = event.getAsJsonArray("categories");
            for (JsonElement cat : cats) {
                TaskPriority p = microsoftCalendarService.resolvePriorityFromCategory(cat.getAsString());
                if (p != null) return p.name();
            }
        }

        TaskPriority p = microsoftCalendarService.resolvePriorityFromImportance(importance);
        return p != null ? p.name() : null;
    }

    private String extractDateTime(JsonObject event, String field) {
        if (!event.has(field) || !event.get(field).isJsonObject()) return null;
        JsonObject dt = event.getAsJsonObject(field);
        String dateTime = getStr(dt, "dateTime");
        String timeZone = getStr(dt, "timeZone");

        if (dateTime == null) return null;

        try {
            ZoneId zone = (timeZone != null) ? ZoneId.of(timeZone) : ZoneId.of(calendarTimeZone);
            ZonedDateTime zdt = java.time.LocalDateTime.parse(dateTime,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")).atZone(zone);
            return zdt.toOffsetDateTime().toString();
        } catch (Exception e) {
            return dateTime;
        }
    }

    private String getStr(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
