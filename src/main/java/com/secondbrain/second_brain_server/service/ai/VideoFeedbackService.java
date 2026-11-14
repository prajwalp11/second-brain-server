package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.entities.CurriculumStep;
import com.secondbrain.second_brain_server.entities.Task;
import com.secondbrain.second_brain_server.entities.VideoCache;
import com.secondbrain.second_brain_server.entities.VideoFeedback;
import com.secondbrain.second_brain_server.enums.Confidence;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.exception.UnauthorizedException;
import com.secondbrain.second_brain_server.repository.CurriculumStepRepository;
import com.secondbrain.second_brain_server.repository.TaskRepository;
import com.secondbrain.second_brain_server.repository.VideoCacheRepository;
import com.secondbrain.second_brain_server.repository.VideoFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records 👍/👎 video feedback and folds it into a video's confidence. Confidence is a volume-aware
 * blend: YouTube-stats confidence dominates with few votes; the Wilson lower-bound of the helpful
 * ratio dominates as votes accrue. When a well-voted video scores poorly it is DEMOTED, so the next
 * resolution of that query skips it and re-resolves a replacement.
 *
 * See docs/curriculum-video-design.md §7A.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VideoFeedbackService {

    /** Below this many votes, lean on the YouTube-stats confidence; above it, lean on user votes. */
    private static final int VOTE_PIVOT = 5;
    /** Need at least this many votes before a low score may demote a video (avoids noise). */
    private static final int DEMOTE_MIN_VOTES = 5;
    /** Demote when the Wilson lower bound of "helpful" falls below this. */
    private static final double DEMOTE_THRESHOLD = 0.40;
    private static final double WILSON_Z = 1.96; // 95% confidence

    private final TaskRepository taskRepository;
    private final CurriculumStepRepository curriculumStepRepository;
    private final VideoFeedbackRepository videoFeedbackRepository;
    private final VideoCacheRepository videoCacheRepository;

    /** Record (or update) a user's vote for the video attached to a task they own. */
    @Transactional
    public void recordForTask(UUID taskId, UUID userId, boolean helpful) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (task.getUser() == null || !task.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Task does not belong to the user");
        }
        String queryKey = resolveQueryKey(task);
        if (queryKey == null) {
            log.debug("Task {} has no curriculum video to rate; ignoring feedback", taskId);
            return;
        }
        record(queryKey, userId, helpful);
    }

    /** Upsert the vote, recompute aggregate counts + effectiveConfidence, and maybe demote. */
    @Transactional
    public void record(String queryKey, UUID userId, boolean helpful) {
        VideoFeedback vote = videoFeedbackRepository.findByQueryKeyAndUserId(queryKey, userId)
                .orElseGet(() -> VideoFeedback.builder().queryKey(queryKey).userId(userId).build());
        vote.setHelpful(helpful);
        videoFeedbackRepository.save(vote);

        long up = videoFeedbackRepository.countByQueryKeyAndHelpful(queryKey, true);
        long down = videoFeedbackRepository.countByQueryKeyAndHelpful(queryKey, false);

        videoCacheRepository.findByQueryKey(queryKey).ifPresent(cache -> {
            cache.setHelpfulCount((int) up);
            cache.setUnhelpfulCount((int) down);
            cache.setEffectiveConfidence(blend(cache.getConfidence(), up, down));
            if (up + down >= DEMOTE_MIN_VOTES && wilsonLowerBound(up, up + down) < DEMOTE_THRESHOLD) {
                cache.setDemoted(true); // next resolve() skips this and picks a replacement
                log.info("Video demoted by feedback: queryKey='{}' ({}👍/{}👎)", queryKey, up, down);
            }
            videoCacheRepository.save(cache);
        });
    }

    // ── scoring ───────────────────────────────────────────────────────────────

    /**
     * Volume-weighted blend of the stats confidence and the user-feedback (Wilson) score.
     * Few votes → mostly stats; many votes → mostly feedback.
     */
    private Confidence blend(Confidence statsConfidence, long up, long down) {
        long votes = up + down;
        double statsScore = toScore(statsConfidence);
        if (votes == 0) {
            return fromScore(statsScore);
        }
        double wilson = wilsonLowerBound(up, votes);
        double w = Math.min(1.0, (double) votes / VOTE_PIVOT); // 0..1 toward feedback as votes grow
        double blended = (1 - w) * statsScore + w * wilson;
        return fromScore(blended);
    }

    /** Wilson score lower bound of the positive proportion — robust at low vote counts. */
    private double wilsonLowerBound(long positive, long total) {
        if (total == 0) return 0.0;
        double phat = (double) positive / total;
        double z = WILSON_Z;
        double denom = 1 + z * z / total;
        double centre = phat + z * z / (2 * total);
        double margin = z * Math.sqrt((phat * (1 - phat) + z * z / (4 * total)) / total);
        return (centre - margin) / denom;
    }

    private double toScore(Confidence c) {
        if (c == null) return 0.5;
        return switch (c) {
            case HIGH -> 0.85;
            case MEDIUM -> 0.6;
            case LOW -> 0.35;
        };
    }

    private Confidence fromScore(double s) {
        if (s >= 0.7) return Confidence.HIGH;
        if (s >= 0.45) return Confidence.MEDIUM;
        return Confidence.LOW;
    }

    private String resolveQueryKey(Task task) {
        if (task.getCurriculumStepId() == null) return null;
        return curriculumStepRepository.findById(task.getCurriculumStepId())
                .map(CurriculumStep::getVideoQueryKey)
                .orElse(null);
    }
}
