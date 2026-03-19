package com.secondbrain.second_brain_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplyAiActionRequest {

    @NotBlank
    private String actionType;

    private Map<String, Object> payload;
}
