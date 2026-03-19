package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgressResponse {

    private UUID domainId;
    private String metricKey;
    private List<TimeSeriesPointDto> timeSeries;
    private List<MilestoneDto> milestones;
    private List<PersonalRecordDto> prs;
}
