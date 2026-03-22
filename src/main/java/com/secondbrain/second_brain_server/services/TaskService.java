package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.CreateTaskRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateTaskStatusRequest;
import com.secondbrain.second_brain_server.dto.response.TaskDto;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final DomainRepository domainRepository;

    public TaskDto createTask(UUID userId, CreateTaskRequest request) {
        // Placeholder
        return null;
    }

    public List<TaskDto> getTasksForUser(UUID userId, List<TaskStatus> statuses, UUID domainId) {
        // Placeholder
        return null;
    }

    public List<TaskDto> getTodayTasks(UUID userId) {
        // Placeholder
        return null;
    }

    public TaskDto updateTaskStatus(UUID taskId, UUID userId, UpdateTaskStatusRequest request) {
        // Placeholder
        return null;
    }

    public void deleteTask(UUID taskId, UUID userId) {
        // Placeholder
    }

    public List<TaskDto> bulkCreateFromAi(UUID userId, List<TaskDto> tasks) {
        // Placeholder
        return null;
    }
}
