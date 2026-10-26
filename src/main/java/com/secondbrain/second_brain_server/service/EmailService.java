package com.secondbrain.second_brain_server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Sends transactional email via the Resend HTTP API (https://resend.com).
 * Failures are logged, not thrown, so the auth flow is never broken by email issues.
 */
@Service
@Slf4j
public class EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    @Value("${resend.api.key:}")
    private String resendApiKey;

    @Value("${mail.from:onboarding@resend.dev}")
    private String mailFrom;

    private final RestTemplate restTemplate;

    public EmailService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void sendPasswordResetCode(String toEmail, String code) {
        String subject = "Your Second Brain password reset code";
        String html = "<p>Your password reset code is:</p>"
                + "<h2 style=\"letter-spacing:4px;\">" + code + "</h2>"
                + "<p>This code expires in 30 minutes. If you didn't request this, you can ignore this email.</p>";
        send(toEmail, subject, html);
    }

    public void sendVerificationCode(String toEmail, String code) {
        String subject = "Verify your Second Brain email";
        String html = "<p>Welcome! Your email verification code is:</p>"
                + "<h2 style=\"letter-spacing:4px;\">" + code + "</h2>"
                + "<p>Enter this in the app to verify your account. It expires in 30 minutes.</p>";
        send(toEmail, subject, html);
    }

    public void sendAccountDeletionCode(String toEmail, String code) {
        String subject = "Confirm your Second Brain account deletion";
        String html = "<p>You requested to <strong>permanently delete</strong> your Second Brain account.</p>"
                + "<p>Your confirmation code is:</p>"
                + "<h2 style=\"letter-spacing:4px;\">" + code + "</h2>"
                + "<p>Entering this code will permanently erase all your domains, logs, tasks and progress. "
                + "This cannot be undone. The code expires in 30 minutes. "
                + "If you didn't request this, ignore this email and your account stays safe.</p>";
        send(toEmail, subject, html);
    }

    private void send(String toEmail, String subject, String html) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("RESEND_API_KEY not configured — skipping email to {}", toEmail);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> body = Map.of(
                    "from", mailFrom,
                    "to", new String[]{toEmail},
                    "subject", subject,
                    "html", html
            );

            restTemplate.postForEntity(RESEND_URL, new HttpEntity<>(body, headers), String.class);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }
}
