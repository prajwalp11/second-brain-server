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

    /** Optional free-text intent/focus to tailor AI generation (e.g. "Spanish, conversational"). */
    private String context;

    @NotNull
    private SkillLevel skillLevel;
}
