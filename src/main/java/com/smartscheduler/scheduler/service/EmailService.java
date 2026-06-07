package com.smartscheduler.scheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendDelegateRequestEmail(String toEmail, String requesterEmail, String magicLink, long expiryHours) {
        log.info("Sending delegate request email to {} from {}", toEmail, requesterEmail);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Calendar Access Request from " + requesterEmail);
            String html = "<div style='font-family:system-ui,sans-serif;max-width:480px;margin:0 auto;padding:32px'>"
                + "<h2 style='color:#0F172A;margin-bottom:8px'>Calendar Access Request</h2>"
                + "<p style='color:#64748B;font-size:14px;line-height:1.6'>"
                + "<strong>" + requesterEmail + "</strong> is requesting permission to create events on your calendar.</p>"
                + "<p style='color:#64748B;font-size:14px;line-height:1.6'>Click the button below to authorize access. "
                + "You'll be asked to sign in with your Google or Microsoft account.</p>"
                + "<a href='" + magicLink + "' style='display:inline-block;padding:12px 24px;background:#0F172A;color:white;"
                + "border-radius:8px;text-decoration:none;font-weight:600;font-size:14px;margin:20px 0'>Authorize Access</a>"
                + "<p style='color:#94A3B8;font-size:12px;margin-top:20px'>This link expires in " + expiryHours + " hours. "
                + "If you didn't expect this request, you can safely ignore this email.</p>"
                + "</div>";
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Delegate request email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send delegate request email to {}", toEmail, e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }
}
