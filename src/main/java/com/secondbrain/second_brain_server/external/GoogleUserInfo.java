package com.secondbrain.second_brain_server.external;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Minimal user info extracted from a verified Google ID token. */
@Getter
@AllArgsConstructor
public class GoogleUserInfo {
    private final String sub;           // Google's stable user id
    private final String email;
    private final String name;
    private final boolean emailVerified;
}
