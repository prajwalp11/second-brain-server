package com.secondbrain.second_brain_server.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateDomainRequest {

    private String customName;
    private SkillLevel skillLevel;
    private String planDescription;
    private String weeklySchedule;
    private DomainStatus status;
}
