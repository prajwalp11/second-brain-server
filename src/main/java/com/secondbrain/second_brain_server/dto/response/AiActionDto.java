package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiActionDto {

    private String type;
    private String description;
    private Map<String, Object> payload;
}
