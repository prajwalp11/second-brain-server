package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedSystemResponse {

    private String planDescription;
    private String weeklySchedule;
    private List<MetricDefinitionResponse> metrics;
    private List<MilestoneResponse> milestones;
    private String linkedResourceUrl;
    private String linkedResourceTitle;
    private List<TaskResponse> tasks;
}
