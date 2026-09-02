package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.service.JobLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resets every user's daily AI usage counter (ai_used_today) to 0 at midnight.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiLimitResetScheduler {

    private final UserRepository userRepository;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 0 0 * * ?") // daily at midnight
    public void resetDailyAiUsage() {
        jobLogService.run("AiLimitResetScheduler", () -> {
            userRepository.resetAllDailyAiUsage();
            return "Reset daily AI usage for all users";
        });
    }
}
