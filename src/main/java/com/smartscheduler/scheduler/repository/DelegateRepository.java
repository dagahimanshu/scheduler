package com.smartscheduler.scheduler.repository;

import com.smartscheduler.scheduler.model.Delegate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DelegateRepository extends JpaRepository<Delegate, Long> {
    List<Delegate> findByRequesterEmailIgnoreCaseAndStatus(String requesterEmail, String status);
    Optional<Delegate> findByRequesterEmailIgnoreCaseAndDelegateEmailIgnoreCase(String requesterEmail, String delegateEmail);
    void deleteByRequesterEmailIgnoreCaseAndDelegateEmailIgnoreCase(String requesterEmail, String delegateEmail);
}
