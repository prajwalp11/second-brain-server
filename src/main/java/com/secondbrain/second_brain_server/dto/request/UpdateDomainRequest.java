package com.secondbrain.second_brain_server.dto.request;

import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDomainRequest {

    private String customName;
    private SkillLevel skillLevel;
    private String planDescription;
    private String weeklySchedule;
    private String linkedResourceUrl;
    private DomainStatus status;
}
