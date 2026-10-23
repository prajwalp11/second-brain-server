package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.UpdateProfileRequest;
import com.secondbrain.second_brain_server.dto.response.UserResponse;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getProfile(@CurrentUser UUID userId) {
        return ResponseEntity.ok(authService.getProfile(userId));
    }

    @PatchMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                      @CurrentUser UUID userId) {
        return ResponseEntity.ok(authService.updateProfile(userId, request));
    }
}
