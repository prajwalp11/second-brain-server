package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedSystemDto {

    private String planDescription;
    private String weeklySchedule;
    private List<MetricDefinitionDto> metrics;
    private List<MilestoneDto> milestones;
    private String linkedResourceUrl;
    private String linkedResourceTitle;
    private List<TaskDto> tasks;
}
