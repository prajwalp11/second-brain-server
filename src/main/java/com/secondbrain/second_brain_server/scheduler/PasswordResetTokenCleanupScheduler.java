package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.repository.PasswordResetTokenRepository;
import com.secondbrain.second_brain_server.service.JobLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Hourly cleanup of used or expired password reset tokens so the table doesn't grow unbounded.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PasswordResetTokenCleanupScheduler {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 0 * * * ?") // top of every hour
    public void cleanupTokens() {
        jobLogService.run("PasswordResetTokenCleanupScheduler", () -> {
            int deleted = passwordResetTokenRepository.deleteUsedOrExpired(LocalDateTime.now());
            return "Deleted " + deleted + " used/expired password reset tokens";
        });
    }
}
