package com.smartscheduler.scheduler.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartscheduler.scheduler.configuration.MicrosoftCalendarConfig;
import com.smartscheduler.scheduler.model.TaskPriority;
import com.smartscheduler.scheduler.model.TaskRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MicrosoftCalendarService {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftCalendarService.class);
    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";
    private static final DateTimeFormatter GRAPH_DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final MicrosoftCalendarConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Value("${google.calendar.time-zone:Asia/Kolkata}")
    private String calendarTimeZone;

    public MicrosoftCalendarService(MicrosoftCalendarConfig config) {
        this.config = config;
    }

    public String createEvent(TaskRequest task, String timezone) throws Exception {
        String accessToken;
        if (task.getDelegateEmail() != null && !task.getDelegateEmail().isBlank()) {
            log.info("Creating event as delegate for: {}", task.getDelegateEmail());
            accessToken = config.getAccessTokenForDelegate(task.getDelegateEmail());
        } else {
            accessToken = config.getAccessToken();
        }

        ZoneId zoneId = ZoneId.of(timezone != null ? timezone : calendarTimeZone);
        ZonedDateTime startAt = LocalDate.parse(task.getDate())
                .atTime(LocalTime.parse(task.getTime()))
                .atZone(zoneId);
        ZonedDateTime endAt;
        if (task.getEndTime() != null && !task.getEndTime().isBlank()) {
            endAt = LocalDate.parse(task.getDate())
                    .atTime(LocalTime.parse(task.getEndTime()))
                    .atZone(zoneId);
        } else {
            endAt = startAt.plusHours(1);
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("subject", resolveSummary(task));

        if (hasText(task.getDescription())) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("contentType", "text");
            body.put("content", task.getDescription().trim());
            event.put("body", body);
        }

        event.put("start", Map.of(
                "dateTime", startAt.format(GRAPH_DT_FMT),
                "timeZone", zoneId.getId()
        ));
        event.put("end", Map.of(
                "dateTime", endAt.format(GRAPH_DT_FMT),
                "timeZone", zoneId.getId()
        ));

        if (task.getPriority() != null) {
            event.put("importance", mapPriorityToImportance(task.getPriority()));
        }

        if (Boolean.TRUE.equals(task.getCreateMeetLink())) {
            event.put("isOnlineMeeting", true);
            event.put("onlineMeetingProvider", "teamsForBusiness");
        }

        if (task.getPriority() != null) {
            event.put("categories", new String[]{task.getPriority().name()});
        }

        Map<String, Object> recurrence = buildMsRecurrence(task.getRecurrence(), task.getRecurrenceCount(), task.getDate());
        if (recurrence != null) {
            event.put("recurrence", recurrence);
            log.info("Set Microsoft recurrence: {}", recurrence);
        }

        String json = gson.toJson(event);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GRAPH_BASE + "/me/events"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            throw new IllegalStateException("Failed to create Outlook event: " + response.body());
        }

        JsonObject created = JsonParser.parseString(response.body()).getAsJsonObject();
        return created.has("webLink") ? created.get("webLink").getAsString() : null;
    }

    public String resolveColorId(String colorId, TaskPriority priority) {
        if (colorId != null && !colorId.isBlank()) return colorId;
        if (priority == null) return null;
        return switch (priority) {
            case URGENT -> "11";
            case HIGH -> "6";
            case MEDIUM -> "5";
            case LOW -> "2";
        };
    }

    public TaskPriority resolvePriorityFromCategory(String category) {
        if (category == null || category.isBlank()) return null;
        try {
            return TaskPriority.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public TaskPriority resolvePriorityFromImportance(String importance) {
        if (importance == null) return null;
        return switch (importance.toLowerCase()) {
            case "high" -> TaskPriority.HIGH;
            case "low" -> TaskPriority.LOW;
            default -> TaskPriority.MEDIUM;
        };
    }

    private String mapPriorityToImportance(TaskPriority priority) {
        return switch (priority) {
            case URGENT, HIGH -> "high";
            case MEDIUM -> "normal";
            case LOW -> "low";
        };
    }

    private String resolveSummary(TaskRequest task) {
        if (hasText(task.getTitle())) return task.getTitle().trim();
        if (hasText(task.getDate()) && hasText(task.getTime()))
            return "Task on " + task.getDate().trim() + " at " + task.getTime().trim();
        if (hasText(task.getDate())) return "Task on " + task.getDate().trim();
        if (hasText(task.getTime())) return "Task at " + task.getTime().trim();
        return "Scheduled task";
    }

    public void updateEventTime(String eventId, String newStart, String newEnd, String timeZone) throws Exception {
        log.info("updateEventTime called: eventId={}, newStart={}, newEnd={}, tz={}", eventId, newStart, newEnd, timeZone);
        String accessToken = config.getAccessToken();

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("start", Map.of("dateTime", newStart, "timeZone", timeZone));
        patch.put("end", Map.of("dateTime", newEnd, "timeZone", timeZone));

        String json = gson.toJson(patch);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GRAPH_BASE + "/me/events/" + eventId))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed to update Outlook event: " + response.body());
        }
    }

    private Map<String, Object> buildMsRecurrence(String recurrence, Integer count, String startDate) {
        if (recurrence == null || recurrence.isBlank()) return null;
        int c = count != null && count > 0 ? count : 0;

        Map<String, Object> pattern = new LinkedHashMap<>();
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("type", "numbered");
        range.put("startDate", startDate);

        switch (recurrence.toUpperCase()) {
            case "DAILY" -> {
                pattern.put("type", "daily");
                pattern.put("interval", 1);
                range.put("numberOfOccurrences", c > 0 ? c : 30);
            }
            case "WEEKLY" -> {
                pattern.put("type", "weekly");
                pattern.put("interval", 1);
                pattern.put("daysOfWeek", new String[]{java.time.LocalDate.parse(startDate).getDayOfWeek().name().substring(0, 1).toUpperCase() + java.time.LocalDate.parse(startDate).getDayOfWeek().name().substring(1).toLowerCase()});
                range.put("numberOfOccurrences", c > 0 ? c : 52);
            }
            case "WEEKDAYS" -> {
                pattern.put("type", "weekly");
                pattern.put("interval", 1);
                pattern.put("daysOfWeek", new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"});
                range.put("numberOfOccurrences", c > 0 ? c : 52);
            }
            case "MONTHLY" -> {
                pattern.put("type", "absoluteMonthly");
                pattern.put("interval", 1);
                pattern.put("dayOfMonth", java.time.LocalDate.parse(startDate).getDayOfMonth());
                range.put("numberOfOccurrences", c > 0 ? c : 12);
            }
            case "YEARLY" -> {
                pattern.put("type", "absoluteYearly");
                pattern.put("interval", 1);
                pattern.put("dayOfMonth", java.time.LocalDate.parse(startDate).getDayOfMonth());
                pattern.put("month", java.time.LocalDate.parse(startDate).getMonthValue());
                range.put("numberOfOccurrences", c > 0 ? c : 5);
            }
            default -> { return null; }
        }

        return Map.of("pattern", pattern, "range", range);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
