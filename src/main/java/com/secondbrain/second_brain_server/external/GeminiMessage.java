package com.secondbrain.second_brain_server.external;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiMessage {
    private String role;
    private List<Map<String, String>> parts;
}
