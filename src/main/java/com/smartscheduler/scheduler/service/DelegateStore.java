package com.smartscheduler.scheduler.service;

import com.smartscheduler.scheduler.model.Delegate;
import com.smartscheduler.scheduler.repository.DelegateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DelegateStore {

    private static final Logger log = LoggerFactory.getLogger(DelegateStore.class);

    private final DelegateRepository delegateRepository;

    public DelegateStore(DelegateRepository delegateRepository) {
        this.delegateRepository = delegateRepository;
    }

    @Transactional
    public void persistDelegate(String delegateEmail, String provider, String requesterEmail) {
        log.info("Persisting authorized delegate: requester={}, delegate={}, provider={}", requesterEmail, delegateEmail, provider);

        var existing = delegateRepository.findByRequesterEmailIgnoreCaseAndDelegateEmailIgnoreCase(requesterEmail, delegateEmail);
        Delegate delegate;
        if (existing.isPresent()) {
            delegate = existing.get();
        } else {
            delegate = new Delegate();
            delegate.setRequesterEmail(requesterEmail);
            delegate.setDelegateEmail(delegateEmail);
        }
        delegate.setProvider(provider);
        delegate.setStatus("AUTHORIZED");
        delegate.setAuthorizedAt(Instant.now().toString());
        delegateRepository.save(delegate);
    }

    public List<Map<String, Object>> listAuthorizedDelegatesMapped(String requesterEmail) {
        return delegateRepository.findByRequesterEmailIgnoreCaseAndStatus(requesterEmail, "AUTHORIZED")
                .stream().map(d -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("email", d.getDelegateEmail());
                    entry.put("provider", d.getProvider());
                    entry.put("authorizedAt", d.getAuthorizedAt());
                    entry.put("requester", d.getRequesterEmail());
                    return entry;
                }).toList();
    }

    @Transactional
    public void removeDelegate(String requesterEmail, String delegateEmail) {
        log.info("Removing delegate: requester={}, delegate={}", requesterEmail, delegateEmail);
        delegateRepository.deleteByRequesterEmailIgnoreCaseAndDelegateEmailIgnoreCase(requesterEmail, delegateEmail);
    }
}
