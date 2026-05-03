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
    private List<TimeSeriesPointResponse> timeSeries;
    private List<MilestoneResponse> milestones;
    private List<PersonalRecordResponse> prs;
}
