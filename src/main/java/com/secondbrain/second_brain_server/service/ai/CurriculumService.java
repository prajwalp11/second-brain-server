package com.secondbrain.second_brain_server.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.second_brain_server.dto.response.GeneratedCurriculumStep;
import com.secondbrain.second_brain_server.dto.response.CurriculumStepResponse;
import com.secondbrain.second_brain_server.entities.CurriculumStep;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.Task;
import com.secondbrain.second_brain_server.entities.VideoCache;
import com.secondbrain.second_brain_server.enums.StepStatus;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.external.GeminiMessage;
import com.secondbrain.second_brain_server.repository.CurriculumStepRepository;
import com.secondbrain.second_brain_server.repository.TaskRepository;
import com.secondbrain.second_brain_server.repository.VideoCacheRepository;
import com.secondbrain.second_brain_server.util.CurriculumValidator;
import com.secondbrain.second_brain_server.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns a domain's ordered curriculum (design §7): persist once, advance step-by-step (never
 * regenerate), extend when finished. Each active step is turned into a Task whose TOPIC comes from
 * the step but whose difficulty is shaped by the user's logs/progress (HYBRID, §7.2). Videos are
 * resolved via VideoResolver and shared through video_cache (D2).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CurriculumService {

    private final CurriculumStepRepository curriculumStepRepository;
    private final TaskRepository taskRepository;
    private final VideoResolver videoResolver;
    private final VideoCacheRepository videoCacheRepository;
    private final com.secondbrain.second_brain_server.repository.DomainRepository domainRepository;
    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    /**
     * Fire-and-forget advance used right after a task is completed: runs in a background thread and
     * its own transaction so the completing request returns immediately (the next task — which needs
     * an AI call + video resolve — is created a moment later and shows up on the next refresh).
     * Takes the domainId (not the entity) to stay safe across threads.
     */
    @org.springframework.scheduling.annotation.Async
    @Transactional
    public void advanceAsync(UUID domainId) {
        try {
            domainRepository.findById(domainId).ifPresent(this::advance);
        } catch (Exception e) {
            log.warn("Async curriculum advance failed for domain {}: {}", domainId, e.getMessage());
        }
    }

    /** Whether a domain is curriculum-backed (vs. the legacy flat-task fallback). */
    public boolean hasCurriculum(UUID domainId) {
        return curriculumStepRepository.countByDomainId(domainId) > 0;
    }

    /** Remove a domain's curriculum (used before regeneration so ordering doesn't collide). */
    @Transactional
    public void clear(UUID domainId) {
        curriculumStepRepository.deleteByDomainId(domainId);
    }

    /** Ordered steps for the course-map view, with video read through from video_cache (D2). */
    public List<CurriculumStepResponse> getCurriculum(UUID domainId) {
        return curriculumStepRepository.findByDomainIdOrderByOrderIndexAsc(domainId).stream()
                .map(this::toResponse)
                .toList();
    }

    private CurriculumStepResponse toResponse(CurriculumStep step) {
        CurriculumStepResponse.CurriculumStepResponseBuilder b = CurriculumStepResponse.builder()
                .id(step.getId())
                .orderIndex(step.getOrderIndex())
                .phase(step.getPhase())
                .title(step.getTitle())
                .description(step.getDescription())
                .status(step.getStatus())
                .estMinutes(step.getEstMinutes());
        if (step.getVideoQueryKey() != null) {
            videoCacheRepository.findByQueryKey(step.getVideoQueryKey()).ifPresent(vc -> {
                b.videoUrl(vc.getVideoUrl());
                b.videoTitle(vc.getVideoTitle());
                b.videoConfidence(vc.getEffectiveConfidence() != null ? vc.getEffectiveConfidence() : vc.getConfidence());
                b.helpfulCount(vc.getHelpfulCount());
                b.unhelpfulCount(vc.getUnhelpfulCount());
            });
        }
        return b.build();
    }

    /**
     * Persist the validated curriculum (step 0 = CURRENT, rest LOCKED), resolve the current step's
     * video, and instantiate its first task. Returns true if a curriculum was persisted.
     */
    @Transactional
    public boolean generateAndPersist(Domain domain, List<GeneratedCurriculumStep> rawCurriculum) {
        String domainName = domainName(domain);
        List<GeneratedCurriculumStep> steps = CurriculumValidator.normalize(rawCurriculum, domainName);
        if (!CurriculumValidator.isUsable(steps)) {
            log.info("Curriculum unusable for domain {} — falling back to flat tasks", domain.getId());
            return false;
        }

        for (GeneratedCurriculumStep s : steps) {
            curriculumStepRepository.save(CurriculumStep.builder()
                    .domain(domain)
                    .orderIndex(s.getOrderIndex())
                    .phase(s.getPhase())
                    .title(s.getTitle())
                    .description(s.getDescription())
                    .estMinutes(s.getEstMinutes())
                    .videoQuery(s.getVideoQuery())
                    .status(s.getOrderIndex() == 0 ? StepStatus.CURRENT : StepStatus.LOCKED)
                    .build());
        }

        // First step at creation: no progress history to calibrate against yet, so skip the AI
        // shaping call and use the step text directly (saves one Gemini call per domain created).
        currentStep(domain.getId()).ifPresent(step -> instantiateTask(domain, step, false));
        log.info("Persisted curriculum ({} steps) for domain {}", steps.size(), domain.getId());
        return true;
    }

    /**
     * Advance the curriculum for a domain: if ALL tasks of the CURRENT step are DONE (D3), mark it
     * DONE, unlock the next step, resolve its video and instantiate its task. If the course is
     * finished, EXTEND it. Returns the number of new tasks created.
     */
    @Transactional
    public int advance(Domain domain) {
        Optional<CurriculumStep> currentOpt = currentStep(domain.getId());
        if (currentOpt.isEmpty()) {
            // No CURRENT step. Do NOT auto-extend here — otherwise every task-completion AND every
            // daily run would fire an AI extend call for an already-finished course. Extend happens
            // exactly once, below, at the moment the final step is completed.
            return 0;
        }

        CurriculumStep current = currentOpt.get();
        if (!allTasksDone(current.getId())) {
            return 0; // step not complete yet — no AI call, no drift
        }

        current.setStatus(StepStatus.DONE);
        curriculumStepRepository.save(current);

        Optional<CurriculumStep> nextOpt = curriculumStepRepository
                .findFirstByDomainIdAndStatusAndOrderIndexGreaterThanOrderByOrderIndexAsc(
                        domain.getId(), StepStatus.LOCKED, current.getOrderIndex());

        if (nextOpt.isEmpty()) {
            return extend(domain); // finished the whole course -> append the next block (one AI call)
        }

        CurriculumStep next = nextOpt.get();
        next.setStatus(StepStatus.CURRENT);
        curriculumStepRepository.save(next);
        instantiateTask(domain, next);
        return 1;
    }

    /** All DONE -> ask the AI for the next block of steps appended after the last orderIndex. */
    @Transactional
    public int extend(Domain domain) {
        List<CurriculumStep> all = curriculumStepRepository.findByDomainIdOrderByOrderIndexAsc(domain.getId());
        if (all.isEmpty()) return 0;
        int baseOrder = all.get(all.size() - 1).getOrderIndex() + 1;

        List<GeneratedCurriculumStep> raw = generateNextBlock(domain, all);
        List<GeneratedCurriculumStep> steps = CurriculumValidator.normalize(raw, domainName(domain));
        if (!CurriculumValidator.isUsable(steps)) {
            log.info("EXTEND produced no usable steps for domain {} — user is all caught up", domain.getId());
            return 0;
        }

        boolean first = true;
        for (GeneratedCurriculumStep s : steps) {
            CurriculumStep saved = curriculumStepRepository.save(CurriculumStep.builder()
                    .domain(domain)
                    .orderIndex(baseOrder + s.getOrderIndex())
                    .phase(s.getPhase())
                    .title(s.getTitle())
                    .description(s.getDescription())
                    .estMinutes(s.getEstMinutes())
                    .videoQuery(s.getVideoQuery())
                    .status(first ? StepStatus.CURRENT : StepStatus.LOCKED)
                    .build());
            if (first) {
                instantiateTask(domain, saved);
                first = false;
            }
        }
        log.info("Extended curriculum (+{} steps) for domain {}", steps.size(), domain.getId());
        return 1;
    }

    // ── internals ───────────────────────────────────────────────────────────

    private Optional<CurriculumStep> currentStep(UUID domainId) {
        return curriculumStepRepository.findFirstByDomainIdAndStatus(domainId, StepStatus.CURRENT);
    }

    private boolean allTasksDone(UUID stepId) {
        List<Task> tasks = taskRepository.findByCurriculumStepId(stepId);
        if (tasks.isEmpty()) return false; // no task yet -> not complete
        return tasks.stream().allMatch(t -> t.getStatus() == TaskStatus.DONE || t.getStatus() == TaskStatus.SKIPPED);
    }

    /**
     * Turn a curriculum step into a Task. Resolves the step's video once (caching the query_key on
     * the step), then builds a task whose topic is the step and whose difficulty the AI shapes from
     * the user's recent progress (hybrid). Falls back to the step's own title/desc if the AI call
     * fails — the step still becomes a usable task.
     */
    private void instantiateTask(Domain domain, CurriculumStep step) {
        instantiateTask(domain, step, true);
    }

    /**
     * Turn a curriculum step into a Task. Resolves the step's video once (caching the query_key on
     * the step). When {@code shape} is true the AI calibrates the task's difficulty to the user's
     * recent progress (hybrid); when false (e.g. the very first step at domain creation, where there
     * is no progress history yet) the step's own title/description are used directly — saving an AI
     * call. Any shaping failure also falls back to the step text.
     */
    private void instantiateTask(Domain domain, CurriculumStep step, boolean shape) {
        // Idempotency: never create a second task (or fire a second AI call) for a step that already
        // has one. Guards against advance() being triggered twice (task completion + daily scheduler).
        if (!taskRepository.findByCurriculumStepId(step.getId()).isEmpty()) {
            return;
        }

        String domainName = domainName(domain);

        VideoResolver.ResolvedVideo video = videoResolver.resolve(step.getVideoQuery(), domainName);
        step.setVideoQueryKey(video.queryKey());
        curriculumStepRepository.save(step);

        TaskDraft draft = shape ? shapeTaskForStep(domain, step)
                : new TaskDraft(step.getTitle(), step.getDescription(), nextDueDate(domain));

        taskRepository.save(Task.builder()
                .user(domain.getUser())
                .domain(domain)
                .curriculumStepId(step.getId())
                .orderIndex(step.getOrderIndex())
                .phase(step.getPhase())
                .title(draft.title())
                .description(draft.description())
                .status(TaskStatus.TODO)
                .dueDate(draft.dueDate())
                .aiGenerated(true)
                .linkedResourceUrl(video.url())
                .linkedResourceTitle(video.title())
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** Hybrid shaping: topic from the step, difficulty/wording from the AI using the user's context. */
    private TaskDraft shapeTaskForStep(Domain domain, CurriculumStep step) {
        // Due date is always the next scheduled training day — the user's schedule is the authority,
        // so we don't let the AI pick a date that could land on a non-training day.
        LocalDate due = nextDueDate(domain);
        try {
            String prompt = promptBuilder.curriculumTask(domain, step.getPhase(), step.getTitle(), step.getDescription());
            List<GeminiMessage> messages = List.of(
                    new GeminiMessage("user", List.of(Map.of("text", "Generate the task for this step now."))));
            String raw = geminiClient.completeWithJson(prompt, messages);
            GeneratedStepTask t = objectMapper.readValue(raw, new TypeReference<GeneratedStepTask>() {});
            if (t != null && t.title != null && !t.title.isBlank()) {
                return new TaskDraft(t.title, t.description != null ? t.description : step.getDescription(), due);
            }
        } catch (Exception e) {
            log.warn("Hybrid task shaping failed for step {} ({}) — using step text: {}",
                    step.getId(), domain.getId(), e.getMessage());
        }
        // Fallback: the step itself is a perfectly good task.
        return new TaskDraft(step.getTitle(), step.getDescription(), due);
    }

    /** Next training day on/after today from the domain schedule; falls back to today if none set. */
    private LocalDate nextDueDate(Domain domain) {
        return DateUtil.nextScheduledOnOrAfter(domain.getWeeklySchedule(), LocalDate.now());
    }

    private List<GeneratedCurriculumStep> generateNextBlock(Domain domain, List<CurriculumStep> done) {
        try {
            String prompt = promptBuilder.curriculumExtend(domain, done);
            List<GeminiMessage> messages = List.of(
                    new GeminiMessage("user", List.of(Map.of("text", "Generate the next block of steps now."))));
            String raw = geminiClient.completeWithJson(prompt, messages);
            return objectMapper.readValue(raw, new TypeReference<List<GeneratedCurriculumStep>>() {});
        } catch (Exception e) {
            log.warn("EXTEND generation failed for domain {}: {}", domain.getId(), e.getMessage());
            return List.of();
        }
    }

    private String domainName(Domain domain) {
        return domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType().name();
    }

    private record TaskDraft(String title, String description, LocalDate dueDate) {}

    private static class GeneratedStepTask {
        public String title;
        public String description;
        public LocalDate dueDate;
    }
}
