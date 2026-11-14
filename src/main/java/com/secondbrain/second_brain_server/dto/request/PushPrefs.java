package com.secondbrain.second_brain_server.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * Per-type push toggles. Stored as JSON in users.push_prefs; a null column means "all enabled".
 * Doubles as the PUT /api/push/prefs request body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PushPrefs {

    @Builder.Default
    private boolean taskDue = true;

    @Builder.Default
    private boolean streakAtRisk = true;

    @Builder.Default
    private boolean weeklyInsight = true;

    /** Defaults used when a user has no stored prefs (null column). */
    public static PushPrefs allEnabled() {
        return PushPrefs.builder().build();
    }
}
