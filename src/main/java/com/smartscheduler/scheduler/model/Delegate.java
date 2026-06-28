package com.smartscheduler.scheduler.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "delegates", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"requesterEmail", "delegateEmail"})
})
public class Delegate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String requesterEmail;

    @Column(nullable = false)
    private String delegateEmail;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String status;

    private String authorizedAt;
}
