package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.CreateTaskRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateTaskStatusRequest;
import com.secondbrain.second_brain_server.dto.response.TaskResponse;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.Task;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.exception.ForbiddenException;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final DomainService domainService;

    @Transactional
    public TaskResponse createTask(UUID userId, CreateTaskRequest request) {
        Domain domain = domainService.assertOwnership(request.getDomainId(), userId);

        Task newTask = Task.builder()
                .user(new User(userId))
                .domain(domain)
                .title(request.getTitle())
                .description(request.getDescription())
                .status(TaskStatus.TODO)
                .dueDate(request.getDueDate())
                .aiGenerated(false)
                .createdAt(LocalDateTime.now())
                .build();

        return new TaskResponse(taskRepository.save(newTask));
    }

    public List<TaskResponse> getTasksForUser(UUID userId, List<TaskStatus> statuses, UUID domainId) {
        List<Task> tasks;
        if (domainId != null) {
            domainService.assertOwnership(domainId, userId);
            tasks = taskRepository.findByDomainId(domainId);
        } else if (statuses != null && !statuses.isEmpty()) {
            tasks = taskRepository.findByUserIdAndStatusIn(userId, statuses);
        } else {
            tasks = taskRepository.findByUserId(userId);
        }
        return tasks.stream().map(TaskResponse::new).collect(Collectors.toList());
    }

    public List<TaskResponse> getUpcomingTasks(UUID userId) {
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysFromNow = today.plusDays(30);
        return taskRepository.findByUserIdAndDueDateBetweenAndStatusIn(userId, today, thirtyDaysFromNow, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS))
                .stream()
                .map(task -> {
                    TaskResponse dto = new TaskResponse(task);
                    if (task.getDueDate() != null) {
                        dto.setDaysRemaining(java.time.temporal.ChronoUnit.DAYS.between(today, task.getDueDate()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public TaskResponse updateTaskStatus(UUID taskId, UUID userId, UpdateTaskStatusRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!task.getUser().getId().equals(userId)) {
            throw new ForbiddenException("User is not authorized to update this task.");
        }

        Optional.ofNullable(request.getStatus()).ifPresent(status -> {
            task.setStatus(status);
            if (status == TaskStatus.DONE) {
                task.setCompletedAt(LocalDateTime.now());
            } else {
                task.setCompletedAt(null);
            }
        });
        Optional.ofNullable(request.getProgress()).ifPresent(task::setProgress);

        return new TaskResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(UUID taskId, UUID userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!task.getUser().getId().equals(userId)) {
            throw new ForbiddenException("User is not authorized to delete this task.");
        }
        taskRepository.delete(task);
    }

    @Transactional
    public List<TaskResponse> bulkCreateFromAi(UUID userId, List<TaskResponse> tasks) {
        List<Task> newTasks = tasks.stream()
                .map(dto -> Task.builder()
                        .user(new User(userId))
                        .domain(domainService.assertOwnership(dto.getDomainId(), userId))
                        .title(dto.getTitle())
                        .description(dto.getDescription())
                        .status(TaskStatus.TODO)
                        .dueDate(dto.getDueDate())
                        .aiGenerated(true)
                        .createdAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());
        return taskRepository.saveAll(newTasks).stream().map(TaskResponse::new).collect(Collectors.toList());
    }
}
