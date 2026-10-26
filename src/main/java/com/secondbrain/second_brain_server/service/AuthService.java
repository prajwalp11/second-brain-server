package com.secondbrain.second_brain_server.service;

import com.secondbrain.second_brain_server.dto.request.LoginRequest;
import com.secondbrain.second_brain_server.dto.request.RegisterRequest;
import com.secondbrain.second_brain_server.dto.request.UpdateProfileRequest;
import com.secondbrain.second_brain_server.dto.response.AuthResponse;
import com.secondbrain.second_brain_server.dto.response.UserResponse;
import com.secondbrain.second_brain_server.entities.OtpToken;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.enums.OtpPurpose;
import com.secondbrain.second_brain_server.exception.EmailNotVerifiedException;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.exception.UnauthorizedException;
import com.secondbrain.second_brain_server.exception.ValidationException;
import com.secondbrain.second_brain_server.repository.OtpTokenRepository;
import com.secondbrain.second_brain_server.repository.UserRepository;
import com.secondbrain.second_brain_server.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final OtpTokenRepository otpTokenRepository;
    private final AccountDeletionService accountDeletionService;
    private final EmailService emailService;

    @Value("${auth.otp.expiry-ms:1800000}")
    private long otpExpiryMs;

    @Value("${auth.otp.max-requests:5}")
    private int otpMaxRequests;

    @Value("${auth.otp.request-window-hours:12}")
    private int otpWindowHours;

    @Value("${auth.otp.max-attempts:10}")
    private int otpMaxAttempts;

    @Transactional
    public String register(RegisterRequest request) {
        if (userRepository.existsByEmailAndIsDeletedFalse(request.getEmail())) {
            throw new ValidationException("Email already registered.");
        }

        User newUser = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .emailVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(newUser);

        // Send the email-verification OTP.
        sendVerificationCodeFor(savedUser);

        return "Registration successful. Please verify your email.";
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmailAndIsDeletedFalse(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Gate: EMAIL accounts must have a verified email. Re-send a code and signal the app.
        if (Boolean.FALSE.equals(user.getEmailVerified())) {
            sendVerificationCodeFor(user);
            throw new EmailNotVerifiedException("Email not verified. A new code has been sent.");
        }

        return buildAuthResponse(user);
    }

    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return user.toResponse();
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
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
     * Starts a password reset. If an EMAIL account exists and the send-rate limit is not exceeded,
     * invalidates outstanding codes and emails a new single-use 6-digit code.
     *
     * @return true if the request was blocked by the rate limit (so the UI can tell the user).
     */
    @Transactional
    public boolean forgotPassword(String email) {
        var userOpt = userRepository.findByEmailAndIsDeletedFalse(email);
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

        String code = issueOtp(user, OtpPurpose.PASSWORD_RESET);
        if (code == null) {
            return true; // rate-limited — signal the UI
        }
        emailService.sendPasswordResetCode(email, code);
        return false;
    }

    /**
     * Completes a password reset: validates the code against the latest unused token,
     * updates the password, and marks the code used.
     */
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ValidationException("Invalid code or email."));

        consumeOtp(user.getId(), OtpPurpose.PASSWORD_RESET, code);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    // ─── Email verification ──────────────────────────────────────────────────────

    /** Verifies the OTP for a user's email and flips email_verified true. */
    @Transactional
    public void verifyEmail(String email, String code) {
        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ValidationException("Invalid code or email."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return; // already verified — idempotent
        }

        consumeOtp(user.getId(), OtpPurpose.EMAIL_VERIFICATION, code);

        user.setEmailVerified(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    /** Resends a verification code (rate-limited). No-op if already verified / unknown email. */
    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmailAndIsDeletedFalse(email).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                return;
            }
            sendVerificationCodeFor(user);
        });
    }

    /** Issues + emails a verification code (rate-limited, invalidates old). */
    private void sendVerificationCodeFor(User user) {
        String code = issueOtp(user, OtpPurpose.EMAIL_VERIFICATION);
        if (code != null) {
            emailService.sendVerificationCode(user.getEmail(), code);
        }
    }

    // ─── Account deletion ──────────────────────────────────────────────────────

    /**
     * Starts account deletion: emails a single-use OTP (rate-limited, invalidates old codes).
     * The actual deletion requires both this code AND the current password in {@link #deleteAccount}.
     */
    @Transactional
    public void requestAccountDeletion(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String code = issueOtp(user, OtpPurpose.ACCOUNT_DELETION);
        if (code == null) {
            throw new ValidationException("Too many requests. Please try again later.");
        }
        emailService.sendAccountDeletionCode(user.getEmail(), code);
    }

    /**
     * Permanently deletes the user's data (domains, logs, tasks, PRs, insights, AI conversations,
     * nudges) and soft-deletes the user row (is_deleted = TRUE, login blocked, email retained).
     * Requires BOTH a valid OTP and the current password.
     */
    @Transactional
    public void deleteAccount(UUID userId, String code, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Verify current password first.
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new ValidationException("Current password is incorrect.");
        }

        // Then verify + consume the OTP.
        consumeOtp(user.getId(), OtpPurpose.ACCOUNT_DELETION, code);

        // Wipe all user-owned data (dedicated service), then soft-delete the user row.
        accountDeletionService.wipeUserData(user.getId());

        // Soft-delete: keep the row + email, block future login, clear the password.
        user.setIsDeleted(true);
        user.setPassword(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Account soft-deleted and data wiped for user {}", user.getId());
    }

    // ─── Shared OTP helpers ───────────────────────────────────────────────────────

    /**
     * Issues a single-use OTP for a user + purpose: enforces the send-rate limit, invalidates any
     * outstanding codes of the same purpose, stores the new code hashed, and returns the raw code
     * (to be emailed by the caller). Returns {@code null} if the rate limit was hit.
     */
    private String issueOtp(User user, OtpPurpose purpose) {
        LocalDateTime windowStart = LocalDateTime.now().minusHours(otpWindowHours);
        long recent = otpTokenRepository.countByUserIdAndPurposeAndCreatedAtAfter(user.getId(), purpose, windowStart);
        if (recent >= otpMaxRequests) {
            log.warn("OTP rate limit hit for user {} purpose {} ({} in {}h); skipping send",
                    user.getId(), purpose, recent, otpWindowHours);
            return null;
        }
        otpTokenRepository.invalidateAllForUser(user.getId(), purpose);

        String code = generateCode();
        OtpToken token = OtpToken.builder()
                .userId(user.getId())
                .purpose(purpose)
                .tokenHash(hashCode(code))
                .expiresAt(LocalDateTime.now().plus(otpExpiryMs, ChronoUnit.MILLIS))
                .used(false)
                .attempts(0)
                .build();
        otpTokenRepository.save(token);
        return code;
    }

    /**
     * Validates an OTP for a user + purpose against the latest unused token (hash, expiry,
     * attempt cap) and marks it used on success. Throws {@link ValidationException} otherwise.
     */
    private void consumeOtp(UUID userId, OtpPurpose purpose, String code) {
        OtpToken token = otpTokenRepository
                .findFirstByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(userId, purpose)
                .orElseThrow(() -> new ValidationException("Invalid or expired code."));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Code has expired. Please request a new one.");
        }
        if (!token.getTokenHash().equals(hashCode(code))) {
            token.setAttempts(token.getAttempts() + 1);
            if (token.getAttempts() >= otpMaxAttempts) {
                token.setUsed(true);
                otpTokenRepository.save(token);
                throw new ValidationException("Too many incorrect attempts. Please request a new code.");
            }
            otpTokenRepository.save(token);
            throw new ValidationException("Invalid code.");
        }

        token.setUsed(true);
        otpTokenRepository.save(token);
    }

    private String generateCode() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private String hashCode(String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
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
