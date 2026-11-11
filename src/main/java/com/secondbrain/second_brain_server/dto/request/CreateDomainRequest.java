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

    /**
     * User-chosen training days as comma-separated 3-letter names (e.g. "Mon,Wed,Fri").
     * When provided, this is a FIXED constraint: the AI plans milestones/pacing around it and
     * must not change it. If null/blank, the AI chooses a schedule as before.
     */
    private String weeklySchedule;

    @NotNull
    private SkillLevel skillLevel;
}
