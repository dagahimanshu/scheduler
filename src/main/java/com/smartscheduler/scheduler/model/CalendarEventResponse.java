package com.smartscheduler.scheduler.model;

public record CalendarEventResponse(
        String id,
        String summary,
        String description,
        String colorId,
        String priority,
        String location,
        String status,
        String start,
        String end,
        String htmlLink
) {
}
