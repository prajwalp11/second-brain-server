package com.secondbrain.second_brain_server.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoFeedbackRequest {

    /** true = 👍 helpful, false = 👎 not helpful. */
    @NotNull
    private Boolean helpful;
}
