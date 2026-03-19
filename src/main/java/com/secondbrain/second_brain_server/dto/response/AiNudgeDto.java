package com.secondbrain.second_brain_server.dto.response;

import com.secondbrain.second_brain_server.enums.NudgeType;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiNudgeDto {

    private UUID id;
    private String message;
    private NudgeType nudgeType;
    private UUID domainId;
    private String domainName;
}
