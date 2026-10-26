package com.secondbrain.second_brain_server.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyEmailRequest {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String code;
}
