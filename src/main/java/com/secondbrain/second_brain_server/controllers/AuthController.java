package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.ForgotPasswordRequest;
import com.secondbrain.second_brain_server.dto.request.LoginRequest;
import com.secondbrain.second_brain_server.dto.request.RegisterRequest;
import com.secondbrain.second_brain_server.dto.request.ResetPasswordRequest;
import com.secondbrain.second_brain_server.dto.request.VerifyEmailRequest;
import com.secondbrain.second_brain_server.dto.request.DeleteAccountRequest;
import com.secondbrain.second_brain_server.dto.response.AuthResponse;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        boolean rateLimited = authService.forgotPassword(request.getEmail());
        String message = rateLimited
                ? "You've reached the password reset limit. Please try again later."
                : "If an account exists for that email, we've sent a 6-digit code.";
        return ResponseEntity.ok(Map.of(
                "status", rateLimited ? "RATE_LIMITED" : "SENT",
                "message", message
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getEmail(), request.getCode());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CurrentUser UUID userId) {
        authService.logout(userId);
        return ResponseEntity.ok().build();
    }

    /** Sends an OTP to confirm account deletion (authenticated). */
    @PostMapping("/request-account-deletion")
    public ResponseEntity<Void> requestAccountDeletion(@CurrentUser UUID userId) {
        authService.requestAccountDeletion(userId);
        return ResponseEntity.ok().build();
    }

    /** Permanently deletes the user's data (soft-deletes the account). Requires OTP + password. */
    @PostMapping("/delete-account")
    public ResponseEntity<Void> deleteAccount(@CurrentUser UUID userId, @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(userId, request.getCode(), request.getPassword());
        return ResponseEntity.ok().build();
    }
}
