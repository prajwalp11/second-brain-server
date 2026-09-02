package com.secondbrain.second_brain_server.service;

import com.secondbrain.second_brain_server.entities.JobExecutionLog;
import com.secondbrain.second_brain_server.repository.JobExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Wraps scheduled job execution and records each run (success or failure)
 * to the job_execution_logs table for auditing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobLogService {

    private final JobExecutionLogRepository jobExecutionLogRepository;

    /**
     * Runs a job that returns a detail string, logging its outcome.
     */
    public void run(String jobName, Supplier<String> task) {
        LocalDateTime startedAt = LocalDateTime.now();
        String details = null;
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            details = task.get();
        } catch (Exception e) {
            status = "FAILED";
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("Job '{}' failed: {}", jobName, errorMessage, e);
        }

        try {
            jobExecutionLogRepository.save(JobExecutionLog.builder()
                    .jobName(jobName)
                    .status(status)
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .errorMessage(errorMessage)
                    .details(details != null && details.length() > 500 ? details.substring(0, 500) : details)
                    .build());
        } catch (Exception logEx) {
            log.error("Failed to write job execution log for '{}': {}", jobName, logEx.getMessage());
        }
    }

    /**
     * Runs a job with no return detail.
     */
    public void run(String jobName, Runnable task) {
        run(jobName, () -> {
            task.run();
            return null;
        });
    }
}
