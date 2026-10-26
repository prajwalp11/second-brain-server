package com.secondbrain.second_brain_server.enums;

/**
 * Purpose of a one-time OTP code. All share the same storage, hashing, expiry,
 * rate-limit and attempt-cap logic ({@code otp_tokens} table) — only the purpose differs.
 */
public enum OtpPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    ACCOUNT_DELETION
}
