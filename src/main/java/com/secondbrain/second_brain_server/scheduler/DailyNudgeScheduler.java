package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.service.ai.AiNudgeService;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyNudgeScheduler {

    private final UserRepository userRepository;
    private final AiNudgeService aiNudgeService;
    

    @Scheduled(cron = "0 0 0 * * ?") // Run daily at midnight
    public void runNightly() {
        userRepository.findAllIds().forEach(userId -> {
            aiNudgeService.generateNudgesForAllDomains(userId);
            
        });
    }
}
