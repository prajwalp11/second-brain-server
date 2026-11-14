package com.secondbrain.second_brain_server.scheduler;

import com.secondbrain.second_brain_server.service.JobLogService;
import com.secondbrain.second_brain_server.service.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SENDER (every 1 min): drains PENDING push_outbox rows to Expo, records history, prunes dead
 * tokens, retries retryable failures (left PENDING up to max attempts). Wrapped in JobLogService.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PushSenderScheduler {

    private final PushService pushService;
    private final JobLogService jobLogService;

    @Scheduled(cron = "0 * * * * ?") // every minute
    public void send() {
        jobLogService.run("PushSenderScheduler", () -> {
            int sent = pushService.dispatchPending();
            return "Sent " + sent + " notifications";
        });
    }
}
