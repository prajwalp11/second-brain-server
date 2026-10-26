package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.repository.OtpTokenRepository;
import com.secondbrain.second_brain_server.service.JobLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Hourly cleanup of used/expired OTP tokens (all purposes: email verification,
 * password reset, account deletion) so the otp_tokens table doesn't grow unbounded.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OtpTokenCleanupScheduler {

    private final OtpTokenRepository otpTokenRepository;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 0 * * * ?") // top of every hour
    public void cleanupTokens() {
        jobLogService.run("OtpTokenCleanupScheduler", () -> {
            int deleted = otpTokenRepository.deleteUsedOrExpired(LocalDateTime.now());
            return "Deleted " + deleted + " used/expired OTP tokens";
        });
    }
}
