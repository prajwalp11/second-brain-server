package com.secondbrain.second_brain_server.dto.response;

import com.secondbrain.second_brain_server.entities.Task;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskDto {

    private UUID id;
    private UUID domainId;
    private String title;
    private String description;
    private TaskStatus status;
    private LocalDate dueDate;
    private Integer progress;
    private boolean aiGenerated;

    public TaskDto(Task task) {
        this.id = task.getId();
        this.domainId = task.getDomain() != null ? task.getDomain().getId() : null;
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.status = task.getStatus();
        this.dueDate = task.getDueDate();
        this.progress = task.getProgress();
        this.aiGenerated = task.isAiGenerated();
    }
}
