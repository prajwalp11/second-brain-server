package com.secondbrain.second_brain_server.dto.response;

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
}
