package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreakDto {

    private UUID domainId;
    private String domainName;
    private Integer currentStreak;
    private Integer longestStreak;
    private LocalDateTime lastLogDate;
}
