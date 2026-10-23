package com.secondbrain.second_brain_server.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Partial profile update. All fields optional:
 *  - firstName / lastName: update display name.
 *  - currentPassword + newPassword: change password (both required together).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateProfileRequest {

    private String firstName;

    private String lastName;

    private String currentPassword;

    @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;
}
