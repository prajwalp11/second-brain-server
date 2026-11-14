package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    /** True for SSO (passwordless) accounts — the app hides the password field on delete. */
    private boolean ssoUser;
}
