package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.service.ai.TaskGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DailyTaskGenerationScheduler {

    private final UserRepository userRepository;
    private final TaskGenerationService taskGenerationService;

    @Scheduled(cron = "0 0 5 * * ?") // Run daily at 5 AM
    public void generateDailyTasks() {
        log.info("Starting daily task generation...");
        userRepository.findAllIds().forEach(userId -> {
            try {
                int generated = taskGenerationService.generateTasksForUser(userId);
                if (generated > 0) {
                    log.info("Generated {} tasks for user {}", generated, userId);
                }
            } catch (Exception e) {
                log.error("Failed to generate tasks for user {}: {}", userId, e.getMessage());
            }
        });
        log.info("Daily task generation complete.");
    }
}
