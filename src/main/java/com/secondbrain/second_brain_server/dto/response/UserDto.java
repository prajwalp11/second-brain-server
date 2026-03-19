package com.secondbrain.second_brain_server.dto.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {

    private UUID id;
    private String name;
    private String email;
    private String profilePictureUrl;
    private String timezone;
}
