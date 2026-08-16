package com.secondbrain.second_brain_server.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.Task;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.external.GeminiMessage;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskGenerationService {

    private final DomainRepository domainRepository;
    private final TaskRepository taskRepository;
    private final UserContextAssembler contextAssembler;
    private final PromptBuilder promptBuilder;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    /**
     * Generate tasks for all active domains of a user that have no pending tasks.
     * Returns the number of tasks generated.
     */
    @Transactional
    public int generateTasksForUser(UUID userId) {
        List<Domain> activeDomains = domainRepository.findByUserIdAndStatus(userId, DomainStatus.ACTIVE);
        if (activeDomains.isEmpty()) {
            log.debug("No active domains for user {}, skipping task generation", userId);
            return 0;
        }

        UserContext context = contextAssembler.assemble(userId);
        int totalGenerated = 0;

        for (Domain domain : activeDomains) {
            try {
                int generated = generateTasksForDomain(userId, domain, context);
                totalGenerated += generated;
            } catch (Exception e) {
                log.error("Failed to generate tasks for user {} domain {}: {}", userId, domain.getId(), e.getMessage());
            }
        }

        log.info("Generated {} tasks for user {} across {} domains", totalGenerated, userId, activeDomains.size());
        return totalGenerated;
    }

    /**
     * Generate tasks for a specific domain if it has no pending tasks.
     */
    @Transactional
    public int generateTasksForDomain(UUID userId, Domain domain, UserContext context) {
        // Check if domain already has pending tasks
        List<Task> pendingTasks = taskRepository.findByDomainIdAndDueDateLessThanEqualAndStatusIn(
                domain.getId(), LocalDate.now().plusDays(30),
                Arrays.asList(TaskStatus.TODO, TaskStatus.IN_PROGRESS));

        // Also check future tasks that haven't expired yet
        List<Task> futureTasks = taskRepository.findByUserIdAndDueDateBetweenAndStatusIn(
                userId, LocalDate.now(), LocalDate.now().plusDays(30),
                Arrays.asList(TaskStatus.TODO, TaskStatus.IN_PROGRESS))
                .stream()
                .filter(t -> t.getDomain() != null && t.getDomain().getId().equals(domain.getId()))
                .collect(Collectors.toList());

        if (!futureTasks.isEmpty()) {
            log.debug("Domain {} already has {} pending tasks, skipping", domain.getId(), futureTasks.size());
            return 0;
        }

        // Generate new tasks via AI
        String systemPrompt = promptBuilder.taskGeneration(context, domain);
        List<GeminiMessage> messages = List.of(
                new GeminiMessage("user", List.of(Map.of("text", "Generate tasks for this domain now.")))
        );

        String rawResponse = geminiClient.completeWithJson(systemPrompt, messages);
        List<GeneratedTask> generatedTasks = parseGeneratedTasks(rawResponse);

        if (generatedTasks.isEmpty()) {
            log.warn("AI returned no tasks for user {} domain {}", userId, domain.getId());
            return 0;
        }

        // Save tasks
        List<Task> newTasks = generatedTasks.stream()
                .map(gt -> Task.builder()
                        .user(domain.getUser())
                        .domain(domain)
                        .title(gt.title)
                        .description(gt.description)
                        .status(TaskStatus.TODO)
                        .dueDate(gt.dueDate)
                        .aiGenerated(true)
                        .createdAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        taskRepository.saveAll(newTasks);
        log.info("Generated {} tasks for domain {} ({})", newTasks.size(), domain.getId(),
                domain.getCustomName() != null ? domain.getCustomName() : domain.getDomainType());

        return newTasks.size();
    }

    private List<GeneratedTask> parseGeneratedTasks(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<GeneratedTask>>() {});
        } catch (Exception e) {
            log.error("Failed to parse AI task generation response: {}", rawJson, e);
            return List.of();
        }
    }

    private static class GeneratedTask {
        public String title;
        public String description;
        public LocalDate dueDate;
    }
}
