package com.smartscheduler.scheduler.model;

import lombok.Data;

@Data
public class DelegateRequest {
    private String id;
    private String requesterEmail;
    private String delegateEmail;
    private String provider;
    private String createdAt;
    private String expiresAt;
    private String status; // PENDING, AUTHORIZED, EXPIRED
}
