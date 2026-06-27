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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);

    @Autowired
    private GoogleCalendarConfig config;

    @Value("${google.calendar.time-zone:Asia/Kolkata}")
    private String calendarTimeZone;

    public String createEvent(TaskRequest task, String timezone, String userEmail) throws Exception {
        log.info("createEvent called: title={}, date={}, time={}, endTime={}, priority={}",
                task.getTitle(), task.getDate(), task.getTime(), task.getEndTime(), task.getPriority());

        Calendar service;
        if (task.getDelegateEmail() != null && !task.getDelegateEmail().isBlank()) {
            log.info("Creating event as delegate for: {}", task.getDelegateEmail());
            service = config.getCalendarServiceForDelegate(task.getDelegateEmail());
        } else {
            service = config.getCalendarService(userEmail);
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

        String rrule = buildRecurrenceRule(task.getRecurrence(), task.getRecurrenceCount());
        if (rrule != null) {
            event.setRecurrence(List.of(rrule));
            log.info("Set recurrence: {}", rrule);
        }

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

    public void updateEventTime(String eventId, String newStart, String newEnd, String timezone, String userEmail) throws Exception {
        log.info("updateEventTime called: eventId={}, newStart={}, newEnd={}", eventId, newStart, newEnd);
        Calendar service = config.getCalendarService(userEmail);

        log.info("Fetching existing event: {}", eventId);
        Event event = service.events().get("primary", eventId).execute();
        log.info("Got event: {} ({})", event.getSummary(), event.getId());

        ZoneId zoneId = ZoneId.of(timezone != null ? timezone : calendarTimeZone);

        log.info("Parsing start: {}", newStart);
        DateTime startDateTime = new DateTime(newStart);
        event.setStart(new EventDateTime().setDateTime(startDateTime).setTimeZone(zoneId.getId()));

        log.info("Parsing end: {}", newEnd);
        DateTime endDateTime = new DateTime(newEnd);
        event.setEnd(new EventDateTime().setDateTime(endDateTime).setTimeZone(zoneId.getId()));

        log.info("Updating event on Google Calendar...");
        service.events().update("primary", eventId, event).execute();
        log.info("Event {} updated successfully", eventId);
    }

    private String buildRecurrenceRule(String recurrence, Integer count) {
        if (recurrence == null || recurrence.isBlank()) return null;
        int c = count != null && count > 0 ? count : 0;
        return switch (recurrence.toUpperCase()) {
            case "DAILY" -> "RRULE:FREQ=DAILY" + (c > 0 ? ";COUNT=" + c : ";COUNT=30");
            case "WEEKLY" -> "RRULE:FREQ=WEEKLY" + (c > 0 ? ";COUNT=" + c : ";COUNT=52");
            case "WEEKDAYS" -> "RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" + (c > 0 ? ";COUNT=" + c : ";COUNT=52");
            case "MONTHLY" -> "RRULE:FREQ=MONTHLY" + (c > 0 ? ";COUNT=" + c : ";COUNT=12");
            case "YEARLY" -> "RRULE:FREQ=YEARLY" + (c > 0 ? ";COUNT=" + c : ";COUNT=5");
            default -> null;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
