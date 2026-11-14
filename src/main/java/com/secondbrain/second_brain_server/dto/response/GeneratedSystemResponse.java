package com.secondbrain.second_brain_server.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneratedSystemResponse {

    private String planDescription;
    private String weeklySchedule;
    private List<MetricDefinitionResponse> metrics;
    private List<MilestoneResponse> milestones;
    private List<TaskResponse> tasks;

    /** Ordered course the server persists as curriculum_step rows (Stage 1). */
    private List<GeneratedCurriculumStep> curriculum;
}
