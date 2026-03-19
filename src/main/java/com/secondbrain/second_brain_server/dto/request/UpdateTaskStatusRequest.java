package com.secondbrain.second_brain_server.dto.request;

import com.secondbrain.second_brain_server.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTaskStatusRequest {

    @NotNull
    private TaskStatus status;

    private Integer progress;
}
