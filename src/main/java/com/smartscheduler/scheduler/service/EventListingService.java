package com.smartscheduler.scheduler.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.smartscheduler.scheduler.configuration.GoogleCalendarConfig;
import com.smartscheduler.scheduler.model.CalendarEventResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class EventListingService {

    private final GoogleCalendarConfig googleCalendarConfig;
    private final CalendarService calendarService;

    @Value("${google.calendar.time-zone:Asia/Kolkata}")
    private String calendarTimeZone;

    public EventListingService(GoogleCalendarConfig googleCalendarConfig, CalendarService calendarService) {
        this.googleCalendarConfig = googleCalendarConfig;
        this.calendarService = calendarService;
    }

    public List<CalendarEventResponse> getNextDayEvents() throws Exception {
        LocalDate nextDay = LocalDate.now(ZoneId.of(calendarTimeZone)).plusDays(1);
        return getEventsForDay(nextDay);
    }

    private List<CalendarEventResponse> getEventsForDay(LocalDate day) throws Exception {
        Calendar service = googleCalendarConfig.getCalendarService();
        ZoneId zoneId = ZoneId.of(calendarTimeZone);
        ZonedDateTime startOfDay = day.atStartOfDay(zoneId);
        ZonedDateTime endOfDay = startOfDay.plusDays(1);

        List<Event> events = service.events()
                .list("primary")
                .setTimeMin(new DateTime(startOfDay.toInstant().toEpochMilli()))
                .setTimeMax(new DateTime(endOfDay.toInstant().toEpochMilli()))
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute()
                .getItems();

        return events.stream()
                .map(this::toResponse)
                .toList();
    }

    private CalendarEventResponse toResponse(Event event) {
        return new CalendarEventResponse(
                event.getId(),
                event.getSummary(),
                event.getDescription(),
                event.getColorId(),
                resolvePriority(event.getColorId()),
                event.getLocation(),
                event.getStatus(),
                extractDateTime(event.getStart()),
                extractDateTime(event.getEnd()),
                event.getHtmlLink()
        );
    }

    private String resolvePriority(String colorId) {
        if (calendarService.resolvePriorityFromColorId(colorId) == null) {
            return null;
        }

        return calendarService.resolvePriorityFromColorId(colorId).name();
    }

    private String extractDateTime(com.google.api.services.calendar.model.EventDateTime eventDateTime) {
        if (eventDateTime == null) {
            return null;
        }

        if (eventDateTime.getDateTime() != null) {
            return eventDateTime.getDateTime().toStringRfc3339();
        }

        if (eventDateTime.getDate() != null) {
            return eventDateTime.getDate().toStringRfc3339();
        }

        return null;
    }
}
