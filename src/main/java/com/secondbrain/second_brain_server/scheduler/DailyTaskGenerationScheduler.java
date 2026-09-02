package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.service.JobLogService;
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
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 0 5 * * ?") // Run daily at 5 AM
    public void generateDailyTasks() {
        jobLogService.run("DailyTaskGenerationScheduler", () -> {
            int[] totalGenerated = {0};
            var ids = userRepository.findAllIds();
            ids.forEach(userId -> {
                try {
                    totalGenerated[0] += taskGenerationService.generateTasksForUser(userId);
                } catch (Exception e) {
                    log.error("Failed to generate tasks for user {}: {}", userId, e.getMessage());
                }
            });
            return "Generated " + totalGenerated[0] + " tasks across " + ids.size() + " users";
        });
    }
}
