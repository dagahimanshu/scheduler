package com.smartscheduler.scheduler.model;

import lombok.Data;

import java.util.List;

@Data
public class TaskRequest {
    private String title;
    private String description;
    private String date;   // yyyy-MM-dd
    private String time;   // HH:mm
    private TaskPriority priority;
    private String colorId;
    private Boolean createMeetLink;
    private List<String> attachmentUrls;
}
