package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.CreateTaskRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateTaskStatusRequest;
import com.secondbrain.second_brain_server.dto.response.TaskDto;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskDto>> getTasks(@RequestParam(required = false) TaskStatus status,
                                                  @RequestParam(required = false) UUID domainId,
                                                  @CurrentUser UUID userId) {
        return ResponseEntity.ok(taskService.getTasksForUser(userId, status != null ? List.of(status) : null, domainId));
    }

    @GetMapping("/today")
    public ResponseEntity<List<TaskDto>> getTodayTasks(@CurrentUser UUID userId) {
        return ResponseEntity.ok(taskService.getTodayTasks(userId));
    }

    @PostMapping
    public ResponseEntity<TaskDto> createTask(@RequestBody CreateTaskRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(taskService.createTask(userId, request));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<TaskDto> updateTask(@PathVariable UUID taskId, @RequestBody UpdateTaskStatusRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(taskService.updateTaskStatus(taskId, userId, request));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID taskId, @CurrentUser UUID userId) {
        taskService.deleteTask(taskId, userId);
        return ResponseEntity.ok().build();
    }
}
