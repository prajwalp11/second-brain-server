package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.CreateTaskRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateTaskStatusRequest;
import com.secondbrain.second_brain_server.dto.response.TaskDto;
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
    private final DomainService domainService; // To assert ownership

    @Transactional
    public TaskDto createTask(UUID userId, CreateTaskRequest request) {
        Domain domain = domainService.assertOwnership(request.getDomainId(), userId);

        Task newTask = Task.builder()
                .user(new User(userId))
                .domain(domain)
                .title(request.getTitle())
                .description(request.getDescription())
                .status(TaskStatus.TODO)
                .dueDate(request.getDueDate())
                .aiGenerated(false) // User created
                .createdAt(LocalDateTime.now())
                .build();

        return new TaskDto(taskRepository.save(newTask)); }

    public List<TaskDto> getTasksForUser(UUID userId, List<TaskStatus> statuses, UUID domainId) {
        List<Task> tasks;
        if (domainId != null) {
            domainService.assertOwnership(domainId, userId); // Ensure user owns domain
            tasks = taskRepository.findByDomainId(domainId);
        } else {
            tasks = taskRepository.findByUserIdAndStatusIn(userId, statuses);
        }
        return tasks.stream().map(TaskDto::new).collect(Collectors.toList());
    }

    public List<TaskDto> getTodayTasks(UUID userId) {
        LocalDate today = LocalDate.now();
        return taskRepository.findByUserIdAndDueDateAndStatusIn(userId, today, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS))
                .stream().map(TaskDto::new).collect(Collectors.toList());
    }

    @Transactional
    public TaskDto updateTaskStatus(UUID taskId, UUID userId, UpdateTaskStatusRequest request) {
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

        return new TaskDto(taskRepository.save(task));
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
    public List<TaskDto> bulkCreateFromAi(UUID userId, List<TaskDto> tasks) {
        List<Task> newTasks = tasks.stream()
                .map(dto -> Task.builder()
                        .user(new User(userId))
                        .domain(domainService.assertOwnership(dto.getDomainId(), userId)) // Ensure domain ownership
                        .title(dto.getTitle())
                        .description(dto.getDescription())
                        .status(TaskStatus.TODO) // AI generated tasks start as TODO
                        .dueDate(dto.getDueDate())
                        .aiGenerated(true)
                        .createdAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());
        return taskRepository.saveAll(newTasks).stream().map(TaskDto::new).collect(Collectors.toList());
    }
}
