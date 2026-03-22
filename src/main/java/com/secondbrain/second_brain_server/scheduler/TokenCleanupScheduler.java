package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    public void cleanExpired() {
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
