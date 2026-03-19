package com.secondbrain.second_brain_server.dto.response;

import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.DomainType;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainDto {

    private UUID id;
    private DomainType domainType;
    private String customName;
    private SkillLevel skillLevel;
    private DomainStatus status;
    private String planDescription;
    private String weeklySchedule;
    private String linkedResourceUrl;
    private String linkedResourceTitle;
    private Integer currentStreak;
    private Integer longestStreak;
    private LocalDate lastLogDate;
    private List<MetricDefinitionDto> metrics;
    private MilestoneDto nextMilestone;
}
