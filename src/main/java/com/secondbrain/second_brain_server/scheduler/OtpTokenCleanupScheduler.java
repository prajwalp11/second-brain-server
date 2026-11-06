package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.repository.OtpTokenRepository;
import com.secondbrain.second_brain_server.repository.RefreshTokenRepository;
import com.secondbrain.second_brain_server.service.JobLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Hourly cleanup of used/expired OTP tokens (all purposes) and revoked/expired refresh tokens,
 * so those tables don't grow unbounded.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OtpTokenCleanupScheduler {

    private final OtpTokenRepository otpTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 0 * * * ?") // top of every hour
    public void cleanupTokens() {
        jobLogService.run("OtpTokenCleanupScheduler", () -> {
            LocalDateTime now = LocalDateTime.now();
            int otp = otpTokenRepository.deleteUsedOrExpired(now);
            int refresh = refreshTokenRepository.deleteRevokedOrExpired(now);
            return "Deleted " + otp + " OTP + " + refresh + " refresh tokens (used/revoked/expired)";
        });
    }
}
