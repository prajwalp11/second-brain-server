package com.secondbrain.second_brain_server.dto.request;

import com.secondbrain.second_brain_server.enums.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterPushTokenRequest {

    @NotBlank
    private String expoToken;

    @NotNull
    private PushPlatform platform;
}
