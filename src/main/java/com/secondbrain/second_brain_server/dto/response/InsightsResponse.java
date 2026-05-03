package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsightsResponse {
    private List<String> highlights;
    private List<String> patterns;
    private List<String> suggestions;
}
