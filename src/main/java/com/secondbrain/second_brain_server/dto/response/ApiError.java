package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiError {

    private Integer status;
    private String message;
    private Map<String, String> errors;
    private LocalDateTime timestamp;
}
