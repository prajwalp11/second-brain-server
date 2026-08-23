package com.secondbrain.second_brain_server.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.*;


import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateTaskRequest {

    private UUID domainId;

    @NotBlank
    private String title;

    private String description;

    private LocalDate dueDate;
}
