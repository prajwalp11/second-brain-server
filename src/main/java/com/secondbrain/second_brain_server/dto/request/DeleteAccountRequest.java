package com.secondbrain.second_brain_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteAccountRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String password;
}
