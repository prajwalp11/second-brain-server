package com.secondbrain.second_brain_server.dto.response;

import com.secondbrain.second_brain_server.enums.Confidence;
import com.secondbrain.second_brain_server.enums.StepStatus;
import lombok.*;

import java.util.UUID;

/** A curriculum step for the app's course-map view; video fields are read through from video_cache. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurriculumStepResponse {
    private UUID id;
    private int orderIndex;
    private String phase;
    private String title;
    private String description;
    private StepStatus status;
    private Integer estMinutes;
    private String videoUrl;
    private String videoTitle;
    private Confidence videoConfidence;
    private int helpfulCount;
    private int unhelpfulCount;
}
