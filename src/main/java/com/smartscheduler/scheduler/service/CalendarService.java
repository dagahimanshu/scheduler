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
                .setSummary(task.getTitle())
                .setDescription(task.getDescription())
                .setColorId(resolveColorId(task))
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

    private String resolveColorId(TaskRequest task) {
        if (task.getColorId() != null && !task.getColorId().isBlank()) {
            return task.getColorId();
        }

        if (task.getPriority() == null) {
            return null;
        }

        return switch (task.getPriority()) {
            case URGENT -> "11";
            case HIGH -> "6";
            case MEDIUM -> "5";
            case LOW -> "2";
        };
    }
}
