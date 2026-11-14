package com.secondbrain.second_brain_server.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * One AI-generated curriculum step (structure + a precise video search query only — never a URL).
 * Validated and normalized by CurriculumValidator before persistence.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneratedCurriculumStep {
    private Integer orderIndex;
    private String phase;        // free-text label or null
    private String title;
    private String description;
    private Integer estMinutes;
    private String videoQuery;   // precise YouTube search phrase for THIS step's skill
}
