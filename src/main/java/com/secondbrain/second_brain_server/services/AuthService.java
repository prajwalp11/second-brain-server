package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.GoogleAuthRequest;
import com.secondbrain.second_brain_server.dto.request.LoginRequest;
import com.secondbrain.second_brain_server.dto.request.RefreshTokenRequest;
import com.secondbrain.second_brain_server.dto.request.RegisterRequest;
import com.secondbrain.second_brain_server.dto.response.AuthResponse;
import com.secondbrain.second_brain_server.entities.RefreshToken;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.external.GoogleTokenVerifier;
import com.secondbrain.second_brain_server.external.GoogleUserInfo;
import com.secondbrain.second_brain_server.repository.NotificationPreferenceRepository;
import com.secondbrain.second_brain_server.repository.RefreshTokenRepository;
import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AuthenticationManager authenticationManager; // Added for password-based login

    public AuthResponse register(RegisterRequest request) {
        // Placeholder for registration logic
        return null;
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        // Placeholder for login logic
        return null;
    }

    public AuthResponse googleAuth(GoogleAuthRequest request) {
        try {
            GoogleUserInfo userInfo = googleTokenVerifier.verify(request.getGoogleToken());
            if (userInfo != null) {
                // Placeholder for Google auth logic
            }
        } catch (GeneralSecurityException | IOException e) {
            // Handle exception
        }
        return null;
    }

    public AuthResponse refreshAccessToken(RefreshTokenRequest request) {
        // Placeholder for refresh token logic
        return null;
    }

    public void logout(UUID userId) {
        // Placeholder for logout logic
    }

    private RefreshToken createRefreshToken(User user) {
        // Placeholder for refresh token creation
        return null;
    }

    private void bootstrapUserDefaults(User user) {
        // Placeholder for bootstrapping user defaults
    }

    private AuthResponse buildAuthResponse(User user) {
        // Placeholder for building auth response
        return null;
    }
}
