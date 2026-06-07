package com.smartscheduler.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

@Service
public class ProviderDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ProviderDetectionService.class);

    private static final String[] GOOGLE_MX_MARKERS = {
            "google.com", "googlemail.com", "aspmx.l.google.com"
    };
    private static final String[] MICROSOFT_MX_MARKERS = {
            "outlook.com", "protection.outlook.com", "mail.protection.outlook.com",
            "olc.protection.outlook.com", "microsoft.com"
    };

    /**
     * Resolves "google" or "microsoft" from an email address by checking MX records.
     * Returns null if the provider cannot be determined.
     */
    public String detectProvider(String email) {
        log.info("detectProvider called with: {}", email);
        if (email == null || !email.contains("@")) {
            log.warn("Invalid email: {}", email);
            throw new IllegalArgumentException("Invalid email address");
        }

        String domain = email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase();
        log.info("Extracted domain: {}", domain);

        if (isKnownGoogleDomain(domain)) {
            log.info("Known Google domain: {}", domain);
            return "google";
        }
        if (isKnownMicrosoftDomain(domain)) {
            log.info("Known Microsoft domain: {}", domain);
            return "microsoft";
        }

        log.info("Unknown domain, performing MX lookup for: {}", domain);
        try {
            String[] mxRecords = lookupMxRecords(domain);
            log.info("MX records for {}: {} records found", domain, mxRecords.length);
            for (String mx : mxRecords) {
                log.debug("  MX: {}", mx);
                String mxLower = mx.toLowerCase();
                for (String marker : GOOGLE_MX_MARKERS) {
                    if (mxLower.contains(marker)) {
                        log.info("MX match: {} contains Google marker '{}'", mx, marker);
                        return "google";
                    }
                }
                for (String marker : MICROSOFT_MX_MARKERS) {
                    if (mxLower.contains(marker)) {
                        log.info("MX match: {} contains Microsoft marker '{}'", mx, marker);
                        return "microsoft";
                    }
                }
            }
            log.warn("No MX match found for domain: {}", domain);
        } catch (Exception e) {
            log.error("MX lookup failed for domain: {}", domain, e);
        }

        return null;
    }

    private boolean isKnownGoogleDomain(String domain) {
        return domain.equals("gmail.com") || domain.equals("googlemail.com");
    }

    private boolean isKnownMicrosoftDomain(String domain) {
        return domain.equals("outlook.com") || domain.equals("hotmail.com")
                || domain.equals("live.com") || domain.equals("msn.com");
    }

    private String[] lookupMxRecords(String domain) throws Exception {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        DirContext ctx = new InitialDirContext(env);

        Attributes attrs = ctx.getAttributes(domain, new String[]{"MX"});
        Attribute mxAttr = attrs.get("MX");

        if (mxAttr == null || mxAttr.size() == 0) {
            ctx.close();
            return new String[0];
        }

        String[] records = new String[mxAttr.size()];
        for (int i = 0; i < mxAttr.size(); i++) {
            records[i] = mxAttr.get(i).toString();
        }

        ctx.close();
        return records;
    }
}
