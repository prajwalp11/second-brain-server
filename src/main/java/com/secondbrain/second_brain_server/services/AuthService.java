package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.LoginRequest;
import com.secondbrain.second_brain_server.dto.request.RegisterRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateProfileRequest;
import com.secondbrain.second_brain_server.dto.response.AuthResponse;
import com.secondbrain.second_brain_server.dto.response.UserDto;
import com.secondbrain.second_brain_server.entities.NotificationPreference;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.exception.UnauthorizedException;
import com.secondbrain.second_brain_server.exception.ValidationException;
import com.secondbrain.second_brain_server.repository.NotificationPreferenceRepository;
import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("Email already registered.");
        }

        User newUser = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(newUser);
        bootstrapUserDefaults(savedUser);
        return buildAuthResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        return buildAuthResponse(user);
    }

    public UserDto getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return user.toDto();
    }

    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Optional.ofNullable(request.getName()).ifPresent(user::setName);
        Optional.ofNullable(request.getTimezone()).ifPresent(user::setTimezone);
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user).toDto();
    }

    public void logout(UUID userId) {
        // For a stateless JWT system, server-side logout typically means doing nothing
        // as the client is responsible for discarding the token.
        // If token invalidation (blacklisting) is required, it would be implemented here.
    }

    private void bootstrapUserDefaults(User user) {
        NotificationPreference defaultPrefs = NotificationPreference.builder()
                .user(user)
                .dailyReminderEnabled(false)
                .weeklyReviewEnabled(false)
                .streakAlertsEnabled(true)
                .prCelebrationEnabled(true)
                .updatedAt(LocalDateTime.now())
                .build();
        notificationPreferenceRepository.save(defaultPrefs);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user);
        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profilePictureUrl(user.getProfilePictureUrl())
                .timezone(user.getTimezone())
                .build();
        return AuthResponse.builder()
                .accessToken(accessToken)
                .user(userDto)
                .build();
    }
}
