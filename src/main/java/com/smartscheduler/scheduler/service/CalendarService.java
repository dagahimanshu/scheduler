package com.smartscheduler.scheduler.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttachment;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.smartscheduler.scheduler.configuration.GoogleCalendarConfig;
import com.smartscheduler.scheduler.model.TaskPriority;
import com.smartscheduler.scheduler.model.TaskRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
public class CalendarService {

    @Autowired
    private GoogleCalendarConfig config;

    @Value("${google.calendar.time-zone:Asia/Kolkata}")
    private String calendarTimeZone;

    public String createEvent(TaskRequest task) throws Exception {

        Calendar service = config.getCalendarService();

        ZoneId zoneId = ZoneId.of(calendarTimeZone);
        ZonedDateTime startAt = LocalDate.parse(task.getDate())
                .atTime(LocalTime.parse(task.getTime()))
                .atZone(zoneId);
        ZonedDateTime endAt = startAt.plusHours(1);

        DateTime startDateTime = new DateTime(startAt.toInstant().toEpochMilli());
        EventDateTime start = new EventDateTime()
                .setDateTime(startDateTime)
                .setTimeZone(zoneId.getId());

        DateTime endDateTime = new DateTime(endAt.toInstant().toEpochMilli());
        EventDateTime end = new EventDateTime()
                .setDateTime(endDateTime)
                .setTimeZone(zoneId.getId());

        Event event = new Event()
                .setSummary(resolveSummary(task))
                .setDescription(resolveDescription(task))
                .setColorId(resolveColorId(task.getColorId(), task.getPriority()))
                .setStart(start)
                .setEnd(end);

        if (Boolean.TRUE.equals(task.getCreateMeetLink())) {
            event.setConferenceData(new ConferenceData().setCreateRequest(
                    new CreateConferenceRequest()
                            .setRequestId(UUID.randomUUID().toString())
                            .setConferenceSolutionKey(new ConferenceSolutionKey().setType("hangoutsMeet"))
            ));
        }

        if (task.getAttachmentUrls() != null && !task.getAttachmentUrls().isEmpty()) {
            event.setAttachments(task.getAttachmentUrls().stream()
                    .map(url -> new EventAttachment().setFileUrl(url))
                    .toList());
        }

        Calendar.Events.Insert insertRequest = service.events().insert("primary", event)
                .setConferenceDataVersion(Boolean.TRUE.equals(task.getCreateMeetLink()) ? 1 : 0)
                .setSupportsAttachments(task.getAttachmentUrls() != null && !task.getAttachmentUrls().isEmpty());

        event = insertRequest.execute();
        return event.getHtmlLink();
    }

    private String resolveSummary(TaskRequest task) {
        if (hasText(task.getTitle())) {
            return task.getTitle().trim();
        }

        if (hasText(task.getDate()) && hasText(task.getTime())) {
            return "Task on " + task.getDate().trim() + " at " + task.getTime().trim();
        }

        if (hasText(task.getDate())) {
            return "Task on " + task.getDate().trim();
        }

        if (hasText(task.getTime())) {
            return "Task at " + task.getTime().trim();
        }

        return "Scheduled task";
    }

    private String resolveDescription(TaskRequest task) {
        if (hasText(task.getDescription())) {
            return task.getDescription().trim();
        }
        return resolveSummary(task);
    }

    public String resolveColorId(String colorId, TaskPriority priority) {
        if (colorId != null && !colorId.isBlank()) {
            return colorId;
        }

        if (priority == null) {
            return null;
        }

        return switch (priority) {
            case URGENT -> "11";
            case HIGH -> "6";
            case MEDIUM -> "5";
            case LOW -> "2";
        };
    }

    public TaskPriority resolvePriorityFromColorId(String colorId) {
        if (colorId == null || colorId.isBlank()) {
            return null;
        }

        return switch (colorId) {
            case "11" -> TaskPriority.URGENT;
            case "6" -> TaskPriority.HIGH;
            case "5" -> TaskPriority.MEDIUM;
            case "2" -> TaskPriority.LOW;
            default -> null;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
