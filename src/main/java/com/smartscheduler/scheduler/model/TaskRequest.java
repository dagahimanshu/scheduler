package com.smartscheduler.scheduler.model;

import lombok.Data;

import java.util.List;

@Data
public class TaskRequest {
    private String title;
    private String description;
    private String date;      // yyyy-MM-dd
    private String time;      // HH:mm (start)
    private String endTime;   // HH:mm (end, optional — defaults to time + 1hr)
    private TaskPriority priority;
    private String colorId;
    private Boolean createMeetLink;
    private List<String> attachmentUrls;
    private String provider;  // "google" (default) or "microsoft"
    private String recurrence; // e.g. "DAILY", "WEEKLY", "WEEKDAYS", "MONTHLY", or null for one-time
    private Integer recurrenceCount; // how many occurrences (default 52 for weekly, 30 for daily)
    private String delegateEmail;
}
