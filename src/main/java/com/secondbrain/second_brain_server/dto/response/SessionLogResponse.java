package com.secondbrain.second_brain_server.dto.response;

import com.secondbrain.second_brain_server.enums.FeelLabel;
import lombok.*;


import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionLogResponse {

    private UUID id;
    private UUID domainId;
    private String sessionType;
    private LocalDate logDate;
    private Integer durationMinutes;
    private Integer feelScore;
    private FeelLabel feelLabel;
    private String notes;
    private String linkedReferenceUrl;
    private Map<String, Double> metrics;
    private String aiInsight;
    private List<PersonalRecordResponse> newPrs;
}
