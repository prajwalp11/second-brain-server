package com.secondbrain.second_brain_server.service;

import com.secondbrain.second_brain_server.dto.request.LoginRequest;
import com.secondbrain.second_brain_server.dto.request.RegisterRequest;
import com.secondbrain.second_brain_server.dto.response.AuthResponse;
import com.secondbrain.second_brain_server.dto.response.UserResponse;

import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.exception.UnauthorizedException;
import com.secondbrain.second_brain_server.exception.ValidationException;

import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final com.secondbrain.second_brain_server.repository.PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${auth.password-reset.expiry-ms:1800000}")
    private long resetExpiryMs;

    @org.springframework.beans.factory.annotation.Value("${auth.password-reset.max-requests:3}")
    private int resetMaxRequests;

    @org.springframework.beans.factory.annotation.Value("${auth.password-reset.request-window-hours:12}")
    private int resetWindowHours;

    @org.springframework.beans.factory.annotation.Value("${auth.password-reset.max-attempts:10}")
    private int resetMaxAttempts;

    @Transactional
    public String register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("Email already registered.");
        }

        User newUser = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(newUser);
        
        return "Registration successful for user: " + savedUser.getEmail();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        return buildAuthResponse(user);
    }

    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return user.toResponse();
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, com.secondbrain.second_brain_server.dto.request.UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            user.setLastName(request.getLastName().trim());
        }

        // Password change — requires both current and new; verify current first.
        boolean wantsPasswordChange = request.getNewPassword() != null && !request.getNewPassword().isBlank();
        if (wantsPasswordChange) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new ValidationException("Current password is required to set a new password.");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new ValidationException("Current password is incorrect.");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user).toResponse();
    }

    /**
     * Starts a password reset. If an EMAIL account exists and the send-rate limit
     * ({@code auth.password-reset.max-requests} per {@code request-window-hours}) is not
     * exceeded, invalidates outstanding codes and emails a new single-use 6-digit code.
     *
     * @return true if the request was blocked by the rate limit (so the UI can tell the user).
     */
    @Transactional
    public boolean forgotPassword(String email) {
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return false; // unknown email — behave like a normal "sent" (no enumeration)
        }
        User user = userOpt.get();

        // SSO-only accounts have no password to reset.
        if (user.getPassword() == null || "GOOGLE".equalsIgnoreCase(user.getAuthProvider())
                || "APPLE".equalsIgnoreCase(user.getAuthProvider())) {
            log.info("Password reset requested for non-EMAIL account {}; skipping", email);
            return false;
        }

        // Rate limit: max N code requests per rolling window (protects email/API cost).
        LocalDateTime windowStart = LocalDateTime.now().minusHours(resetWindowHours);
        long recentRequests = passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), windowStart);
        if (recentRequests >= resetMaxRequests) {
            log.warn("Password reset rate limit hit for user {} ({} in {}h); skipping send",
                    user.getId(), recentRequests, resetWindowHours);
            return true; // signal the UI to show "limit reached"
        }

        // Invalidate any outstanding codes so only the newest is valid.
        passwordResetTokenRepository.invalidateAllForUser(user.getId());

        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        var token = com.secondbrain.second_brain_server.entities.PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(hashCode(code))
                .expiresAt(LocalDateTime.now().plus(resetExpiryMs, java.time.temporal.ChronoUnit.MILLIS))
                .used(false)
                .attempts(0)
                .build();
        passwordResetTokenRepository.save(token);
        emailService.sendPasswordResetCode(email, code);
        return false;
    }

    /**
     * Completes a password reset: validates the code against the latest unused
     * token (hash match, not expired), updates the password, and marks it used.
     */
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ValidationException("Invalid code or email."));

        var token = passwordResetTokenRepository
                .findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new ValidationException("Invalid or expired code."));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Code has expired. Please request a new one.");
        }
        if (!token.getTokenHash().equals(hashCode(code))) {
            // Wrong code — count the attempt; invalidate the code once the cap is hit.
            token.setAttempts(token.getAttempts() + 1);
            if (token.getAttempts() >= resetMaxAttempts) {
                token.setUsed(true);
                passwordResetTokenRepository.save(token);
                throw new ValidationException("Too many incorrect attempts. Please request a new code.");
            }
            passwordResetTokenRepository.save(token);
            throw new ValidationException("Invalid code.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
    }

    private String hashCode(String code) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }


    @Transactional
    public void logout(UUID userId) {
        log.info("User {} logged out", userId);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .build();
    }
}
