package com.secondbrain.second_brain_server.dto.request;

import com.secondbrain.second_brain_server.enums.DomainType;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDomainRequest {

    @NotNull
    private DomainType domainType;

    private String customName;

    @NotNull
    private SkillLevel skillLevel;

    private String linkedResourceUrl;
}
