package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.CreateTaskRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateTaskStatusRequest;
import com.secondbrain.second_brain_server.dto.response.TaskResponse;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.service.TaskService;
import com.secondbrain.second_brain_server.service.ai.TaskGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskGenerationService taskGenerationService;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(@RequestParam(required = false) TaskStatus status,
                                                  @RequestParam(required = false) UUID domainId,
                                                  @CurrentUser UUID userId) {
        return ResponseEntity.ok(taskService.getTasksForUser(userId, status != null ? List.of(status) : null, domainId));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<TaskResponse>> getUpcomingTasks(@CurrentUser UUID userId) {
        return ResponseEntity.ok(taskService.getUpcomingTasks(userId));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(taskService.createTask(userId, request));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable UUID taskId, @Valid @RequestBody UpdateTaskStatusRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(taskService.updateTaskStatus(taskId, userId, request));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID taskId, @CurrentUser UUID userId) {
        taskService.deleteTask(taskId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Integer>> generateTasks(@CurrentUser UUID userId) {
        int generated = taskGenerationService.generateTasksForUser(userId);
        return ResponseEntity.ok(Map.of("tasksGenerated", generated));
    }
}
