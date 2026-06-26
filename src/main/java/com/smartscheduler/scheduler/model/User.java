package com.smartscheduler.scheduler.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String passwordHash;

    private String name;

    private String calendarProvider;

    private boolean calendarConnected;

    private String createdAt;

    private String loginMethod;
}
