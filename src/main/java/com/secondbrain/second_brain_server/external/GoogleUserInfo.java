package com.secondbrain.second_brain_server.external;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleUserInfo {
    private String sub;
    private String email;
    private String name;
    private String picture;
}
